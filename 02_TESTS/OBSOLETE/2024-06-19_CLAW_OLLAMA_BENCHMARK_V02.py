import time
import json
import sys
import os
from pathlib import Path

# Add 01_SRC to path
project_root = Path(__file__).parent.parent.absolute()
sys.path.insert(0, str(project_root / "01_SRC"))

import importlib
providers = importlib.import_module("2024-06-19_CLAW_PROVIDERS_V01")
stream = providers.stream
TextChunk = providers.TextChunk
AssistantTurn = providers.AssistantTurn

def benchmark_model(model_name, prompt="Explain quantum entanglement in 2 paragraphs."):
    print(f"\nBenchmarking {model_name}...")

    config = {
        "model": model_name,
        "max_tokens": 1024,
        "no_tools": True
    }

    system = "You are a helpful assistant."
    messages = [{"role": "user", "content": prompt}]

    start_time = time.time()
    first_token_time = None
    total_tokens = 0
    full_text = ""

    try:
        # Mocking for environments where Ollama is not running
        if os.environ.get("MOCK_BENCHMARK") == "1":
            time.sleep(0.5) # TTFT simulation
            first_token_time = time.time()
            simulated_response = "Quantum entanglement is a phenomenon where particles become correlated in such a way that the state of one cannot be described independently of the others, even when separated by large distances. This leads to instantaneous changes in the state of one particle when the other is measured, a property Einstein famously called 'spooky action at a distance'."
            full_text = simulated_response
            total_tokens = len(full_text.split())
            time.sleep(1.0) # Generation time simulation
        else:
            for event in stream(model_name, system, messages, [], config):
                if isinstance(event, TextChunk):
                    if first_token_time is None:
                        first_token_time = time.time()
                    full_text += event.text
                    total_tokens += len(event.text.split())
                elif isinstance(event, AssistantTurn):
                    if event.out_tokens > 0:
                        total_tokens = event.out_tokens
    except Exception as e:
        print(f"Error benchmarking {model_name}: {e}")
        return None

    end_time = time.time()

    if first_token_time is None:
        print(f"Failed to get response from {model_name}")
        return None

    ttft = (first_token_time - start_time) * 1000 # ms
    total_time = end_time - start_time
    # Avoid division by zero
    gen_time = (end_time - first_token_time)
    tps = total_tokens / gen_time if gen_time > 0 else 0

    results = {
        "model": model_name,
        "ttft_ms": ttft,
        "total_time_s": total_time,
        "tokens": total_tokens,
        "tps": tps,
        "mocked": os.environ.get("MOCK_BENCHMARK") == "1"
    }

    print(f"  TTFT: {ttft:.2f} ms")
    print(f"  TPS:  {tps:.2f} tokens/s")
    print(f"  Total Time: {total_time:.2f} s")

    return results

def main():
    test_models = ["ollama/qwen2.5:0.5b", "ollama/llama3.2:1b"]

    # Check if Ollama is running, if not, use mock
    import urllib.request
    try:
        urllib.request.urlopen("http://localhost:11434", timeout=1)
    except:
        print("Ollama not detected at localhost:11434. Enabling MOCK_BENCHMARK.")
        os.environ["MOCK_BENCHMARK"] = "1"

    results = []
    for model in test_models:
        res = benchmark_model(model)
        if res:
            results.append(res)

    if results:
        dest = Path("03_DOCS/2024-06-19_CLAW_OLLAMA_BENCHMARKS_V01.json")
        dest.parent.mkdir(exist_ok=True)
        with open(dest, "w") as f:
            json.dump(results, f, indent=2)
        print(f"\nBenchmarks saved to {dest}")

if __name__ == "__main__":
    main()
