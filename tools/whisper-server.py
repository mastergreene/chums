import io
import os
import sys
from aiohttp import web
from faster_whisper import WhisperModel

print("Loading faster-whisper model (tiny.en)...", flush=True)
model = WhisperModel("tiny.en", device="cpu", compute_type="int8")
print("Whisper model ready on port 50061", flush=True)

async def handle_transcribe(request):
    try:
        reader = await request.multipart()
        audio_bytes = None
        while True:
            part = await reader.next()
            if part is None:
                break
            if part.name == "file":
                audio_bytes = await part.read(decode=False)
                break

        if not audio_bytes:
            return web.json_response({"error": "no file uploaded"}, status=400)

        segments, info = model.transcribe(io.BytesIO(audio_bytes), beam_size=1, language="en")
        text = " ".join([s.text for s in segments]).strip()
        print(f"Transcribed: '{text}'", flush=True)
        return web.json_response({"text": text})
    except Exception as e:
        print(f"Error in transcription: {e}", file=sys.stderr, flush=True)
        return web.json_response({"error": str(e)}, status=500)

async def handle_health(request):
    return web.json_response({"status": "ok"})

app = web.Application(client_max_size=32 * 1024 * 1024)
app.router.add_post("/v1/audio/transcriptions", handle_transcribe)
app.router.add_get("/health", handle_health)
app.router.add_get("/", handle_health)

if __name__ == "__main__":
    port = int(os.environ.get("WHISPER_PORT", 50061))
    web.run_app(app, host="127.0.0.1", port=port)
