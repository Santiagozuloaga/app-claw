"""
Ollama Benchmarking Script for ClawSpring.

This script measures the performance (TTFT, TPS, and total duration) of Ollama models.
Requires a running Ollama instance at http://localhost:11434.

Usage:
    PYTHONPATH=01_SRC:00_SOPORTE python3 2026-07-04_CLAW_OLLAMA_BENCHMARK_V01.py [model_name]

Note: If no model_name is provided, it defaults to 'qwen2.5:0.5b'.
"""

import time
import json
import sys
import os
import urllib.request
import urllib.error

# Add relevant directories to sys.path
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.append(os.path.join(BASE_DIR, "01_SRC"))
sys.path.append(os.path.join(BASE_DIR, "00_SOPORTE"))

def benchmark_model(model_name: str, base_url: str = "http://localhost:11434"):
    prompt = "Explain quantum entanglement in one paragraph."
    url = f"{base_url.rstrip('/')}/api/chat"

    payload = {
        "model": model_name,
        "messages": [{"role": "user", "content": prompt}],
        "stream": True
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")

    print(f"Starting benchmark for model: {model_name}...")

    start_time = time.perf_counter()
    first_token_time = None
    tokens_count = 0

    try:
        with urllib.request.urlopen(req) as response:
            for line in response:
                if not line:
                    continue

                chunk = json.loads(line.decode("utf-8"))
                if first_token_time is None:
                    first_token_time = time.perf_counter()

                if not chunk.get("done"):
                    tokens_count += 1
                else:
                    total_duration = time.perf_counter() - start_time
                    ttft = first_token_time - start_time
                    tps = tokens_count / (total_duration - ttft) if (total_duration - ttft) > 0 else 0

                    return {
                        "model": model_name,
                        "success": True,
                        "total_duration_s": round(total_duration, 4),
                        "ttft_s": round(ttft, 4),
                        "tokens_per_second": round(tps, 2),
                        "total_tokens": tokens_count,
                        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
                    }
    except urllib.error.URLError as e:
        print(f"Error connecting to Ollama: {e}")
        return {"model": model_name, "success": False, "error": str(e)}
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return {"model": model_name, "success": False, "error": str(e)}

def main():
    model = sys.argv[1] if len(sys.argv) > 1 else "qwen2.5:0.5b"
    result = benchmark_model(model)

    if result["success"]:
        print("\nBenchmark Results:")
        print(json.dumps(result, indent=2))

        output_file = os.path.join(BASE_DIR, "03_DOCS", f"2026-07-04_CLAW_OLLAMA_BENCHMARKS_{model.replace(':', '_')}.json")
        with open(output_file, "w") as f:
            json.dump(result, f, indent=2)
        print(f"\nResults saved to {output_file}")
    else:
        print("\nBenchmark failed. Ensure Ollama is running and the model is pulled.")
        sys.exit(1)

if __name__ == "__main__":
    main()
