"""Port the Gallery's generated composition graph to Kotlin projection calls.

This is an offline source importer, not a markup parser shipped in the app.
The original graph, paths, timing, and easing values are retained.
"""
import re
import sys
from pathlib import Path

upstream = Path(sys.argv[1]) / 'WinUIGallery/Assets/AnimatedVisuals/LottieLogo1.cs'
output = Path(__file__).resolve().parents[1] / 'src/winuiMain/kotlin/io/github/composefluent/winrt/gallery/GalleryLottieSource.kt'
source = upstream.read_text(encoding='utf-8-sig')
start = source.index('        // Rectangle Path 1')
end = source.index('        internal AnimatedVisual(Compositor compositor)')
body = source[start:end]
local_methods = set(re.findall(r'^        \w+ (\w+)\(\)', body, re.M))
fields = re.findall(r'^        (\w+) (_\w+);', source[:start], re.M)

def lower_member(match):
    name = match[1]
    if name in {'Open', 'Closed', 'Round', 'Flat', 'Square', 'Miter', 'Bevel', 'Fill', 'Alternate', 'NonZero', 'Normal', 'Auto', 'SetToInitialValue', 'SetToFinalValue'}:
        return '.' + name
    return '.' + name[0].lower() + name[1:]

# The source has no interpolated strings. Preserve expression-animation strings
# byte-for-byte, including their native property spelling.
strings = []
def stash(match):
    strings.append(match[0]); return f'STRINGTOKEN{len(strings)-1}TOKEN'
body = re.sub(r'"(?:[^"\\]|\\.)*"', stash, body)
body = re.sub(r'^        (\w+) (\w+)\(\)', r'    private fun \2(): \1', body, flags=re.M)
body = re.sub(r'^        ', '    ', body, flags=re.M)
body = body.replace('TimeSpan.FromTicks(c_durationTicks)', '5_967_000_000L.nanoseconds')
body = re.sub(r'new (Vector2|Vector3|Matrix3x2)\(([^()]*)\)', lambda m: m[1] + '(' + ', '.join((v.strip().removesuffix('F') + 'f') for v in m[2].split(',')) + ')', body)
body = re.sub(r'new (\w+)\(', r'\1(', body)
body = re.sub(r'Microsoft.UI.ColorHelper.FromArgb\(([^)]+)\)', lambda m: 'Color(' + ', '.join(v.strip() + 'u' for v in m[1].split(',')) + ')', body)
# Lift C# assignment expressions into Kotlin's also to preserve cache writes.
body = re.sub(r'var result = (_\w+) = (.*);', r'val result = \2.also { \1 = it }', body)
body = re.sub(r'return (_\w+) = (.*);', r'return \2.also { \1 = it }', body)
body = re.sub(r'\bvar (\w+) =', r'val \1 =', body)
# Several paths reuse the controller variable for two independently animated properties.
body = body.replace('val controller =', 'var controller =')
body = re.sub(r'\bCanvasGeometry result;', 'lateinit var result: CanvasGeometry', body)
body = re.sub(r'using \(val builder = CanvasPathBuilder\(null\)\)\s*\{', 'CanvasPathBuilder(_device).use { builder ->', body)
body = re.sub(r'\.([A-Z]\w*)', lower_member, body)
# The asset requires every composition/geometry factory to produce an object.
# Keep that asset invariant at creation, while WinRT projection results stay nullable.
factory_ends = []
for match in re.finditer(r'(?:_c|CanvasGeometry)\.create\w*\(', body):
    depth = 1
    index = match.end()
    while depth:
        depth += (body[index] == '(') - (body[index] == ')')
        index += 1
    factory_ends.append(index)
for index in reversed(factory_ends):
    body = body[:index] + '!!' + body[index:]
# These collections/property sets are created with their owning composition
# object. The projection keeps reference properties nullable at the ABI boundary.
body = re.sub(r'\.(shapes|children|properties)\.', r'.\1!!.', body)
body = re.sub(r'(val \w+ = )(\w+\.(?:shapes|children|properties))(?=;|\s*$)', r'\1checkNotNull(\2)', body, flags=re.M)
body = re.sub(r'(tryGetAnimationController\([^\n]*?\))', r'\1!!', body)
body = re.sub(r'(?<=\d)F\b', 'f', body)
# Composition scalar properties and keyframes use floats; StepCount uses UInt.
body = re.sub(r'(\.\w+\s*=\s*)(-?\d+)(;|\s*$)', r'\1\2f\3', body, flags=re.M)
body = re.sub(r'(\.stepCount\s*=\s*)(\d+)f', r'\1\2u', body)
for method in ['insertKeyFrame', 'insertScalar', 'setScalarParameter']:
    body = re.sub(r'(\.' + method + r'\()([^\n]*)(\);)', lambda m: m[1] + re.sub(r'(?<![\w.])(-?\d+)(?=\s*[,\)])', r'\1f', m[2] + ')')[:-1] + m[3], body)
body = body.replace(';', '')
for i, string in enumerate(strings):
    body = body.replace(f'STRINGTOKEN{i}TOKEN', string.replace('$', '\\$'))
header = '''// Generated port of MIT-licensed WinUI Gallery Assets/AnimatedVisuals/LottieLogo1.cs.
// Regenerate with tools/import-lottie-source.py <WinUI-Gallery checkout>.
package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.WinRTOut
import io.github.composefluent.winrt.runtime.asWinRT
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import microsoft.graphics.canvas.CanvasDevice
import microsoft.graphics.canvas.ICanvasResourceCreator
import microsoft.graphics.canvas.geometry.*
import microsoft.ui.composition.*
import microsoft.ui.xaml.controls.IAnimatedVisual
import microsoft.ui.xaml.controls.IAnimatedVisualSource
import windows.foundation.numerics.*
import windows.ui.Color

class GalleryLottieSource : IAnimatedVisualSource {
    override fun tryCreateAnimatedVisual(compositor: Compositor?, diagnostics: WinRTOut<Any?>): IAnimatedVisual {
        diagnostics.value = null
        return GalleryLottieVisual(requireNotNull(compositor))
    }
}

internal class GalleryLottieVisual(private val _c: Compositor) : IAnimatedVisual {
    private val _device = requireNotNull(CanvasDevice.getSharedDevice()).asWinRT<ICanvasResourceCreator>()
    private val _reusableExpressionAnimation = requireNotNull(_c.createExpressionAnimation())
'''
declarations = '\n'.join(f'    private lateinit var {name}: {kind}' for kind, name in fields)
footer = '''
    init { Root() }
    override val rootVisual: Visual get() = _root
    override val duration: Duration get() = 5_967_000_000L.nanoseconds
    override val size: Vector2 get() = Vector2(375f, 667f)
    override fun close() { _root.close(); _reusableExpressionAnimation.close() }
}
'''
output.write_text(header + declarations + '\n\n' + body + footer, encoding='utf-8')
