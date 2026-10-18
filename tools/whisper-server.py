# Speech to text for the hub where whisperkit-cli isn't (Linux): faster-whisper behind the same endpoint,
# POST /v1/audio/transcriptions (multipart: file, and optional prompt and language) -> {"text": ...}.
# The hub starts it (hub/hub.mjs, startWhisper) with WHISPER_PORT and WHISPER_MODEL; its packages are in
# tools/whisper-requirements.txt.
import asyncio
import io
import os
import sys

import av
from aiohttp import web

# faster-whisper 1.2 passes metadata_errors= to av.open, which PyAV 15 and later no longer take
_av_open = av.open
def _open(*args, **kwargs):
    kwargs.pop('metadata_errors', None)
    return _av_open(*args, **kwargs)
av.open = _open

from faster_whisper import WhisperModel  # noqa: E402  (after the shim, which it must see)

PORT = int(os.environ.get('WHISPER_PORT', 50061))
MODEL = os.environ.get('WHISPER_MODEL', 'tiny.en')

print(f'loading faster-whisper model {MODEL}', file=sys.stderr, flush=True)
model = WhisperModel(MODEL, device='cpu', compute_type='int8')
print(f'whisper ready on 127.0.0.1:{PORT}', file=sys.stderr, flush=True)


def transcribe(audio, prompt, language):
    # the segments are a generator: decoding happens while they are read, so read them here, off the event loop
    segments, _ = model.transcribe(io.BytesIO(audio), beam_size=1, language=language, initial_prompt=prompt)
    return ' '.join(s.text for s in segments).strip()


async def handle_transcribe(request):
    fields, audio = {}, None
    reader = await request.multipart()
    while (part := await reader.next()) is not None:
        if part.name == 'file':
            audio = await part.read(decode=False)
        elif part.name:
            fields[part.name] = await part.text()
    if not audio:
        return web.json_response({'error': 'no file uploaded'}, status=400)
    language = fields.get('language') or ('en' if MODEL.endswith('.en') else None)
    try:
        text = await asyncio.get_running_loop().run_in_executor(None, transcribe, audio, fields.get('prompt') or None, language)
    except Exception as e:  # a clip it can't decode: an answer, not a crash
        print(f'transcription failed: {e}', file=sys.stderr, flush=True)
        return web.json_response({'error': str(e)}, status=500)
    return web.json_response({'text': text})


async def handle_health(request):
    return web.json_response({'status': 'ok', 'model': MODEL})


app = web.Application(client_max_size=32 * 1024 * 1024)
app.router.add_post('/v1/audio/transcriptions', handle_transcribe)
app.router.add_get('/health', handle_health)
app.router.add_get('/', handle_health)

if __name__ == '__main__':
    web.run_app(app, host='127.0.0.1', port=PORT, print=None)
