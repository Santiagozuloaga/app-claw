import time
import sys
from pathlib import Path

# Add 01_SRC to path
sys.path.append(str(Path(__file__).parent.parent / "01_SRC"))

try:
    from providers import stream, TextChunk, AssistantTurn
    PROVIDERS_AVAILABLE = True
except ImportError:
    PROVIDERS_AVAILABLE = False

def run_benchmark(model_name):
    if not PROVIDERS_AVAILABLE:
        print("Providers module not found")
        return

    print(f"Benchmarking model: {model_name}")

    prompt = "Explain quantum entanglement in 100 words."
    messages = [{"role": "user", "content": prompt}]
    config = {"max_tokens": 512}

    start_time = time.time()
    ttft = None
    total_tokens = 0
    full_text = ""

    try:
        for event in stream(f"ollama/{model_name}", "You are a helpful assistant.", messages, [], config):
            if isinstance(event, TextChunk):
                if ttft is None:
                    ttft = time.time() - start_time
                full_text += event.text
            elif isinstance(event, AssistantTurn):
                end_time = time.time()
                total_duration = end_time - start_time
                # Estimate tokens (4 chars per token)
                total_tokens = len(full_text) / 4
                tps = total_tokens / (total_duration - ttft) if total_duration > ttft else 0

                print(f"  - TTFT: {ttft:.4f}s")
                print(f"  - Total Duration: {total_duration:.4f}s")
                print(f"  - Estimated TPS: {tps:.2f} tokens/s")
                print(f"  - Output length: {len(full_text)} chars")
                return {
                    "model": model_name,
                    "ttft": ttft,
                    "duration": total_duration,
                    "tps": tps
                }
    except Exception as e:
        print(f"  - Error benchmarking {model_name}: {e}")
        return None

if __name__ == "__main__":
    from providers import list_ollama_models, PROVIDERS
    ollama_url = PROVIDERS.get("ollama", {}).get("base_url", "http://localhost:11434")
    models = list_ollama_models(ollama_url)

    if not models:
        print("No local Ollama models found or server not running.")
    else:
        results = []
        for model in models[:3]: # Benchmark first 3 models
            res = run_benchmark(model)
            if res:
                results.append(res)

        if results:
            print("\nSummary:")
            print(f"{'Model':<20} | {'TTFT (s)':<8} | {'TPS':<8}")
            print("-" * 40)
            for r in results:
                print(f"{r['model']:<20} | {r['ttft']:<8.4f} | {r['tps']:<8.2f}")
