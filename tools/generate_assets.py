"""Generate original procedural sprite assets and the v1 JSON Schema (stdlib only)."""
import json
import math
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent


def png(name, alpha):
    size = 32
    rows = bytearray()
    for y in range(size):
        rows.append(0)
        for x in range(size):
            distance = math.hypot((x + .5 - size / 2) / (size / 2), (y + .5 - size / 2) / (size / 2))
            rows.extend((255, 255, 255, max(0, min(255, round(alpha(distance) * 255)))))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(bytes(rows))) + chunk(b'IEND', b'')
    path = ROOT / 'shared/src/main/resources/assets/xirc_vfx_studio/textures/effect' / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def number(low, high, default=None, integer=False):
    result = {'type': 'integer' if integer else 'number', 'minimum': low, 'maximum': high}
    if default is not None:
        result['default'] = default
    return result


def nova_sheet():
    """Original 16-frame expanding turbulent ring; no external texture inputs."""
    size, tile = 256, 64
    pixels = bytearray()
    for y in range(size):
        pixels.append(0)
        for x in range(size):
            frame = (y // tile) * 4 + x // tile
            t = frame / 15
            dx, dy = ((x % tile + .5) / tile * 2 - 1), ((y % tile + .5) / tile * 2 - 1)
            radius, angle = math.hypot(dx, dy), math.atan2(dy, dx)
            target = .12 + .72 * t
            wobble = .035 * math.sin(angle * 9 + t * 8) + .02 * math.sin(angle * 17 - t * 12)
            ring = math.exp(-((radius - target - wobble) / (.06 - .025 * t)) ** 2)
            core = math.exp(-radius * radius / .03) * (1 - t) ** 3
            alpha = min(1, ring + core) * (1 - t * .85)
            pixels.extend((255, 255, 255, max(0, min(255, round(alpha * 255)))))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(pixels))) + chunk(b'IEND', b'')
    (ROOT / 'shared/src/main/resources/assets/xirc_vfx_studio/textures/effect/nova_sheet.png').write_bytes(data)


def obj(properties, required=()):
    return {'type': 'object', 'additionalProperties': False, 'properties': properties, 'required': list(required)}


def array(item, low, high):
    return {'type': 'array', 'items': item, 'minItems': low, 'maxItems': high}


def vector(low, high, default=None):
    result = array(number(low, high), 3, 3)
    if default is not None:
        result['default'] = default
    return result


def value(low, high, default, curves=True):
    options = [number(low, high), obj({'min': number(low, high), 'max': number(low, high)}, ('min', 'max'))]
    if curves:
        options.append(obj({'curve': array(obj({'time': number(0, 1), 'value': number(low, high), 'easing': easing()}, ('time', 'value')), 2, 64)}, ('curve',)))
    return {'oneOf': options, 'default': default}


def choice(values, default):
    return {'type': 'string', 'enum': values, 'default': default}


def easing():
    result = obj({'mode': choice(['linear', 'step', 'smooth', 'bezier'], 'linear'), 'controls': array(number(0, 1), 4, 4)})
    result['allOf'] = [{'if': {'required': ['mode'], 'properties': {'mode': {'const': 'bezier'}}}, 'then': {'required': ['controls']}, 'else': {'not': {'required': ['controls']}}}]
    return result


def schema():
    color = array(number(0, 1), 3, 4)
    resource = {'type': 'string', 'maxLength': 512, 'pattern': r'^(?!.*\.\.)(?!.*//)[a-z0-9_.-]+:[a-z0-9_./-]*[a-z0-9_-]$'}
    texture = {'type': 'string', 'maxLength': 512, 'pattern': r'^(?!.*\.\.)(?!.*//)[a-z0-9_.-]+:textures/[a-z0-9_./-]+\.png$', 'default': 'xirc_vfx_studio:textures/effect/soft.png'}
    emitter = obj({
        'id': {'type': 'string', 'pattern': '^[a-z][a-z0-9_]{0,63}$'},
        'parent': {'type': 'string', 'pattern': '^([a-z][a-z0-9_]{0,63})?$', 'default': ''},
        'folder': {'type': 'string', 'maxLength': 64, 'default': ''},
        'kind': choice(['particle', 'beam', 'trail', 'slash', 'ring_mesh', 'cube_mesh', 'sphere_mesh', 'lightning'], 'particle'),
        'space': choice(['local', 'world'], 'local'),
        'offset': vector(-128, 128, [0, 0, 0]),
        'transform': obj({'pathInterpolation': choice(['linear', 'spline'], 'linear'), 'rotation': vector(-36000, 36000, [0, 0, 0]), 'scale': number(.01, 8, 1), 'keys': array(obj({'time': number(0, 631), 'position': vector(-128, 128, [0, 0, 0]), 'rotation': vector(-36000, 36000, [0, 0, 0]), 'scale': number(.01, 8, 1), 'easing': easing()}, ('time',)), 2, 64)}),
        'start': number(0, 300, 0),
        'duration': number(.05, 300),
        'rate': number(0, 2000, 30),
        'bursts': array(obj({'time': number(0, 300, 0), 'count': number(1, 2000, 1, True)}), 0, 64),
        'lifetime': value(.05, 30, 1, False),
        'size': value(.001, 10, .1),
        'speed': value(-50, 50, 1),
        'rotation': value(-36000, 36000, 0),
        'spin': number(-3600, 3600, 0),
        'opacity': number(0, 1, 1),
        'aspect': number(.05, 20, 1),
        'color': {'oneOf': [color, obj({'gradient': array(obj({'time': number(0, 1), 'color': color, 'easing': easing()}, ('time', 'color')), 2, 64)}, ('gradient',))], 'default': [.7, .3, 1, 1]},
        'shape': choice(['point', 'sphere', 'ring', 'spiral', 'cone', 'box'], 'point'),
        'radius': number(0, 64, .5),
        'spread': number(0, 1, .2),
        'direction': vector(-1, 1, [0, 1, 0]),
        'force': vector(-100, 100, [0, 0, 0]),
        'drag': number(0, 20, 0),
        'noise': number(0, 20, 0),
        'curl': number(0, 50, 0),
        'curlFrequency': number(.01, 10, 1),
        'attractor': vector(-128, 128, [0, 0, 0]),
        'attraction': number(-100, 100, 0),
        'falloff': number(0, 4, 1),
        'vortexAxis': vector(-1, 1, [0, 1, 0]),
        'vortex': number(-100, 100, 0),
        'vortexPull': number(-100, 100, 0),
        'orbitRadius': vector(0, 64, [0, 0, 0]),
        'orbitSpeed': number(-3600, 3600, 0),
        'path': array(vector(-128, 128), 2, 64),
        'pathLoop': {'type': 'boolean', 'default': False},
        'pathCycles': number(.05, 10, 1),
        'pathScatter': number(0, 8, 0),
        'alignVelocity': {'type': 'boolean', 'default': False},
        'stretch': number(0, 4, 0),
        'collision': {'type': 'boolean', 'default': False},
        'bounce': number(0, 1, .5),
        'texture': texture,
        'glow': {'type': 'boolean', 'default': True},
        'fade': number(0, 1, .3),
        'length': number(.01, 128, 3),
        'trailPoints': number(2, 64, 12, True),
        'pixelSize': number(1, 8, 1, True),
        'meshScale': value(.001, 8, 1),
        'bloom': number(0, 4, 0),
        'blend': choice(['alpha', 'additive'], 'alpha'),
        'textureAnimation': obj({'columns': number(1, 64, 1, True), 'rows': number(1, 64, 1, True), 'frames': number(1, 4096, integer=True), 'fps': number(.1, 120, 20), 'loop': {'type': 'boolean', 'default': True}}),
    }, ('id',))
    emitter['allOf'] = [{'if': {'required': ['pixelSize'], 'properties': {'pixelSize': {'minimum': 2}}}, 'then': {'required': ['kind'], 'properties': {'kind': {'enum': ['slash', 'ring_mesh', 'cube_mesh', 'sphere_mesh', 'lightning']}}}}]
    emitter['allOf'].append({'if': {'required': ['meshScale']}, 'then': {'required': ['kind'], 'properties': {'kind': {'enum': ['slash', 'ring_mesh', 'cube_mesh', 'sphere_mesh', 'lightning']}}}})
    emitter['allOf'].append({'if': {'required': ['textureAnimation']}, 'then': {'properties': {'kind': {'enum': ['particle', 'beam', 'trail']}}}})
    emitter['allOf'].append({'if': {'required': ['aspect']}, 'then': {'properties': {'kind': {'const': 'particle'}}}})
    emitter['allOf'].append({'if': {'anyOf': [{'required': ['alignVelocity']}, {'required': ['stretch']}]}, 'then': {'properties': {'kind': {'const': 'particle'}}}})
    result = obj({
        'schemaVersion': {'const': 1},
        'id': resource,
        'name': {'type': 'string', 'maxLength': 512},
        'author': {'type': 'string', 'maxLength': 512, 'default': 'Xirc'},
        'description': {'type': 'string', 'maxLength': 512},
        'duration': number(.05, 300, 5),
        'autoDuration': {'type': 'boolean', 'default': False},
        'pivot': vector(-128, 128, [0, 0, 0]),
        'wind': vector(-100, 100, [0, 0, 0]),
        'loop': {'type': 'boolean', 'default': False},
        'seed': number(-2147483648, 2147483647, 0, True),
        'markers': array(obj({'name': {'type': 'string', 'maxLength': 128}, 'time': number(0, 631)}, ('name', 'time')), 0, 64),
        'parameters': array(obj({'name': {'type': 'string', 'pattern': '^[a-z][a-z0-9_]{0,31}$'}, 'label': {'type': 'string', 'maxLength': 128}, 'min': number(-1000000, 1000000), 'max': number(-1000000, 1000000), 'value': number(-1000000, 1000000), 'targets': array(obj({'layer': {'type': 'string', 'pattern': '^[a-z][a-z0-9_]{0,63}$'}, 'property': {'enum': ['size', 'speed', 'rotation', 'lifetime', 'meshScale', 'opacity', 'spin', 'aspect', 'start', 'duration', 'rate', 'radius', 'length', 'spread', 'drag', 'noise', 'bloom', 'fade', 'bounce']}, 'scale': number(-1000000, 1000000), 'offset': number(-1000000, 1000000)}, ('layer', 'property')), 1, 64)}, ('name', 'min', 'max', 'value', 'targets')), 0, 32),
        'emitters': array({'$ref': '#/$defs/emitter'}, 1, 64),
    }, ('schemaVersion', 'id', 'emitters'))
    result.update({'$schema': 'https://json-schema.org/draft/2020-12/schema', '$id': 'https://github.com/XircTwerk/xirc_vfx_studio/blob/main/schema/effect.schema.json', 'title': "Xirc's VFX Studio effect v1", '$defs': {'emitter': emitter}})
    result['description'] = 'Canonical standalone effect format. Runtime additionally checks hierarchy, sorted keyframes, min<=max, burst times, resources and particle budgets. Photon imports convert to this format with explicit diagnostics; no Photon runtime dependency.'
    path = ROOT / 'schema/effect.schema.json'
    path.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    png('soft', lambda r: 1 if r <= .75 else max(0, (1 - r) / .25))
    png('spark', lambda r: max(0, 1 - r * r) ** .6)
    png('ring', lambda r: max(0, 1 - abs(r - .65) / .15))
    nova_sheet()
    schema()
    material = obj({'schemaVersion': {'const': 1}, 'nodes': array(obj({
        'operation': {'enum': ['dissolve', 'outline', 'fresnel', 'distortion', 'scroll', 'gradient', 'chromatic', 'pixelate']},
        'amount': number(0, 1, .5), 'speed': number(-10, 10, 1), 'scale': number(1, 64, 8),
        'color': array(number(0, 1), 4, 4), 'enabled': {'type': 'boolean'},
        'keys': array(obj({'time': number(0, 1), 'value': number(0, 1)}, ('time', 'value')), 0, 64)
    }, ('operation',)), 0, 16)}, ('schemaVersion', 'nodes'))
    material['$schema'] = 'https://json-schema.org/draft/2020-12/schema'
    material['title'] = "Xirc's VFX image material v1"
    (ROOT / 'schema/material.schema.json').write_text(json.dumps(material, indent=2) + '\n', encoding='utf-8')
