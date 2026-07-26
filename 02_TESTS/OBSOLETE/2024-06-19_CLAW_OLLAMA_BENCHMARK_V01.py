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
        for event in stream(model_name, system, messages, [], config):
            if isinstance(event, TextChunk):
                if first_token_time is None:
                    first_token_time = time.time()
                full_text += event.text
                # Rough token estimate
                total_tokens += len(event.text.split()) # This is a very rough estimate
            elif isinstance(event, AssistantTurn):
                # If provider gives exact tokens, use them
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
    tps = total_tokens / (end_time - first_token_time) if (end_time - first_token_time) > 0 else 0

    results = {
        "model": model_name,
        "ttft_ms": ttft,
        "total_time_s": total_time,
        "tokens": total_tokens,
        "tps": tps
    }

    print(f"  TTFT: {ttft:.2f} ms")
    print(f"  TPS:  {tps:.2f} tokens/s")
    print(f"  Total Time: {total_time:.2f} s")

    return results

def main():
    # Only run if Ollama is likely available or we want to test the script logic
    test_models = ["ollama/qwen2.5:0.5b", "ollama/llama3.2:1b"]

    results = []
    for model in test_models:
        res = benchmark_model(model)
        if res:
            results.append(res)

    if results:
        with open("03_DOCS/2024-06-19_CLAW_OLLAMA_BENCHMARKS_V01.json", "w") as f:
            json.dump(results, f, indent=2)
        print("\nBenchmarks saved to 03_DOCS/2024-06-19_CLAW_OLLAMA_BENCHMARKS_V01.json")

if __name__ == "__main__":
    main()
