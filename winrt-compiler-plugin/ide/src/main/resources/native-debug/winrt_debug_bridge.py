"""LLDB SB API adapter for the IDE's Kotlin/Native debug process.

The IDE owns launch configuration and UI; LLDB owns breakpoints, stepping,
DWARF, stack unwinding and values. This module never interprets Kotlin layout.
"""
import json
import os
import socket
import sys
from pathlib import Path

import lldb

sys.path.insert(0, str(Path(__file__).parent))
from winrt_lldb import load_kotlin_formatters


class Session:
    def __init__(self, debugger, send):
        self.debugger = debugger
        self.send = send
        self.target = None
        self.process = None
        self.values = {}
        self.next_value = 1
        self.last_state = None
        self.last_stop = None
        self.generation = 0
        self.startup_loader_break = False
        self.listener = lldb.SBListener("Kotlin WinRT process events")
        self.pending_attach_stop = False

    def checked(self, error):
        if error.Fail():
            raise RuntimeError(error.GetCString())

    def detach(self):
        if not self.process or not self.process.IsValid() or self.process.GetState() in (lldb.eStateExited, lldb.eStateDetached):
            return
        # The Windows process plugin can leave threads suspended when detaching
        # directly from a step. Resume through its normal state transition first.
        # https://lldb.llvm.org/cpp_reference/ProcessWindows_8cpp_source.html
        self.target.DeleteAllBreakpoints()
        if self.process.GetState() == lldb.eStateStopped:
            self.checked(self.process.Continue())
        if self.process.GetState() not in (lldb.eStateExited, lldb.eStateDetached):
            self.checked(self.process.Detach())

    def stopped(self):
        if not self.process or self.process.GetState() != lldb.eStateStopped:
            raise RuntimeError("The native process is not suspended.")

    def thread(self, request):
        self.stopped()
        thread = self.process.GetThreadByID(int(request["thread"]))
        if not thread.IsValid():
            raise RuntimeError("The selected native thread no longer exists.")
        self.process.SetSelectedThread(thread)
        return thread

    def frame(self, request):
        if request["generation"] != self.generation:
            raise RuntimeError("The native stack has changed. Refresh the suspended frame.")
        thread = self.thread(request)
        frame = thread.GetFrameAtIndex(int(request["frame"]))
        if not frame.IsValid():
            raise RuntimeError("The selected native frame no longer exists.")
        thread.SetSelectedFrame(int(request["frame"]))
        return frame

    def value(self, value):
        key = self.next_value
        self.next_value += 1
        self.values[key] = value
        return {"id": key, "name": value.GetName() or "", "type": value.GetTypeName() or "",
                "value": value.GetSummary() or value.GetValue() or "",
                "children": value.MightHaveChildren(), "generation": self.generation}

    def dispatch(self, request):
        command = request["command"]
        if command == "configure":
            if self.process:
                raise RuntimeError("Detach the current native process before replacing its executable.")
            if self.target:
                self.debugger.DeleteTarget(self.target)
            else:
                load_kotlin_formatters(self.debugger, request["konanHome"])
            self.target = self.debugger.CreateTarget(request["executable"])
            if not self.target.IsValid():
                raise RuntimeError("LLDB could not load the native executable.")
            self.debugger.SetSelectedTarget(self.target)
            self.debugger.SetAsync(True)
            return {}
        if not self.target:
            raise RuntimeError("Configure the native executable first.")
        if command == "breakpoint":
            breakpoint = self.target.BreakpointCreateByLocation(request["file"], int(request["line"]))
            breakpoint.SetOneShot(bool(request.get("once", False)))
            return {"id": breakpoint.GetID(), "locations": breakpoint.GetNumLocations()}
        if command == "removeBreakpoint":
            return {"removed": self.target.BreakpointDelete(int(request["breakpoint"]))}
        if command == "attach":
            error = lldb.SBError()
            self.process = self.target.AttachToProcessWithID(self.listener, int(request["pid"]), error)
            self.checked(error)
            self.pending_attach_stop = True
            return {"pid": self.process.GetProcessID()}
        if command == "resumeStartup":
            self.stopped()
            self.startup_loader_break = True
            self.checked(self.process.Continue())
            return {}
        if command == "launch":
            info = lldb.SBLaunchInfo(request.get("arguments", []))
            info.SetListener(self.listener)
            info.SetWorkingDirectory(request["directory"])
            error = lldb.SBError()
            self.process = self.target.Launch(info, error)
            self.checked(error)
            return {"pid": self.process.GetProcessID()}
        if command == "detachForRestart":
            self.detach()
            self.process = None
            self.values.clear()
            self.last_state = None
            self.last_stop = None
            self.pending_attach_stop = False
            self.startup_loader_break = False
            self.generation += 1
            return {}
        if command == "detach":
            self.detach()
            return {}
        if command == "pause":
            self.checked(self.process.Stop())
            return {}
        if command in ("resume", "stepOver", "stepInto", "stepOut"):
            self.stopped()
            self.values.clear()
            if command == "resume":
                self.checked(self.process.Continue())
            else:
                thread = self.thread(request)
                {"stepOver": thread.StepOver, "stepInto": thread.StepInto, "stepOut": thread.StepOut}[command]()
            return {}
        if command == "stack":
            thread = self.thread(request)
            frames = []
            start = max(0, int(request.get("start", 0)))
            for index in range(start, min(thread.GetNumFrames(), start + 100)):
                frame = thread.GetFrameAtIndex(index)
                entry = frame.GetLineEntry()
                source = entry.GetFileSpec()
                frames.append({"index": index, "name": frame.GetFunctionName() or "<native>",
                               "file": str(Path(source.GetDirectory() or "") / source.GetFilename()) if source.GetFilename() else "",
                               "line": entry.GetLine(), "generation": self.generation})
            return {"frames": frames, "total": thread.GetNumFrames()}
        if command == "variables":
            frame = self.frame(request)
            variables = frame.GetVariables(True, True, False, True)
            start = max(0, int(request.get("start", 0)))
            return {"values": [self.value(variables.GetValueAtIndex(i)) for i in range(start, min(variables.GetSize(), start + 100))], "total": variables.GetSize()}
        if command == "children":
            self.frame(request)
            value = self.values.get(int(request["value"]))
            if value is None:
                raise RuntimeError("The native value is no longer available.")
            start = max(0, int(request.get("start", 0)))
            count = value.GetNumChildren()
            return {"values": [self.value(value.GetChildAtIndex(i)) for i in range(start, min(count, start + 100))], "total": count}
        raise RuntimeError("Unsupported native debugger command: " + command)

    def poll(self):
        if not self.process or not self.process.IsValid():
            return
        # State polling observes internal single-step stops that LLDB immediately
        # restarts. Only publish non-restarted public process events to the IDE.
        state = None
        event = lldb.SBEvent()
        while self.listener.GetNextEvent(event):
            if lldb.SBProcess.EventIsProcessEvent(event) and not lldb.SBProcess.GetRestartedFromEvent(event):
                state = lldb.SBProcess.GetStateFromEvent(event)
        if self.pending_attach_stop:
            self.pending_attach_stop = False
            state = self.process.GetState()
        stop = self.process.GetStopID()
        if state is not None and (state != self.last_state or (state == lldb.eStateStopped and stop != self.last_stop)):
            self.last_state, self.last_stop = state, stop
            if state == lldb.eStateStopped:
                if self.startup_loader_break:
                    self.startup_loader_break = False
                    # Windows emits a loader breakpoint after a CREATE_SUSPENDED
                    # startup is released. Consume only that first ntdll INT3;
                    # never suppress application breakpoints or other exceptions.
                    # https://learn.microsoft.com/windows-hardware/drivers/debugger/initial-breakpoint
                    reasons = [thread for thread in self.process if thread.GetStopReason() not in (lldb.eStopReasonNone, lldb.eStopReasonInvalid)]
                    if len(reasons) == 1:
                        thread = reasons[0]
                        module = thread.GetFrameAtIndex(0).GetModule().GetFileSpec().GetFilename() or ""
                        if (thread.GetStopReason() == lldb.eStopReasonException and module.lower() == "ntdll.dll"
                                and "0x80000003" in (thread.GetStopDescription(1024) or "").lower()):
                            self.checked(self.process.Continue())
                            return
                self.values.clear()
                self.generation += 1
                threads = []
                for thread in self.process:
                    threads.append({"id": thread.GetThreadID(), "name": thread.GetName() or "Thread " + str(thread.GetThreadID()),
                                    "reason": thread.GetStopReason()})
                selected = self.process.GetSelectedThread().GetThreadID()
                stopped = next((item["id"] for item in threads if item["reason"] in (lldb.eStopReasonBreakpoint, lldb.eStopReasonPlanComplete, lldb.eStopReasonException)), selected)
                self.send({"event": "stopped", "thread": stopped, "threads": threads, "generation": self.generation})
            elif state in (lldb.eStateRunning, lldb.eStateStepping):
                self.send({"event": "running"})
            elif state in (lldb.eStateExited, lldb.eStateDetached):
                self.send({"event": "terminated", "exitCode": self.process.GetExitStatus()})
        for stream, read in (("stdout", self.process.GetSTDOUT), ("stderr", self.process.GetSTDERR)):
            output = read(16384)
            if output:
                self.send({"event": "output", "stream": stream, "text": output})


def run(debugger):
    # Passed by the IDE, never read from project .lldbinit or executable arguments.
    configuration = json.loads(os.environ.pop("KOTLIN_WINRT_DEBUG_BRIDGE"))
    connection = socket.create_connection(("127.0.0.1", configuration["port"]), timeout=10)
    connection.settimeout(0.05)

    def send(message):
        payload = (json.dumps(message, ensure_ascii=True) + "\n").encode("utf-8")
        connection.settimeout(10)
        try:
            connection.sendall(payload)
        finally:
            connection.settimeout(0.05)

    session = Session(debugger, send)
    try:
        send({"token": configuration["token"]})
        buffer = b""
        while True:
            session.poll()
            try:
                received = connection.recv(65536)
            except socket.timeout:
                continue
            if not received:
                break
            buffer += received
            if len(buffer) > 1024 * 1024:
                raise RuntimeError("Native debugger request exceeds the message limit.")
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                request = json.loads(line)
                try:
                    result = session.dispatch(request)
                    # Evaluations can advance LLDB's internal stop ID without a user step.
                    if request["command"] in ("variables", "children"):
                        session.last_stop = session.process.GetStopID()
                    send({"id": request["id"], "result": result})
                except Exception as error:
                    send({"id": request["id"], "error": str(error)})
                session.poll()
                if request["command"] == "detach":
                    return
    finally:
        try:
            session.detach()
        finally:
            connection.close()
