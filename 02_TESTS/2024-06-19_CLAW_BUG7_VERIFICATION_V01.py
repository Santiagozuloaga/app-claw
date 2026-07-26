import os
import sys
import time
from pathlib import Path

# Add 01_SRC to path
project_root = Path(__file__).parent.parent.absolute()
sys.path.insert(0, str(project_root / "01_SRC"))

def test_thinking_env_with_ttl_cache():
    """Verify that thinking capabilities react to env changes after TTL expires."""
    import importlib
    thinking = importlib.import_module("2024-06-19_CLAW_THINKING_V01")
    modelo_soporta_thinking = thinking.modelo_soporta_thinking

    model = "claude-custom-tier"

    # Ensure env is clean
    os.environ.pop("ANTHROPIC_DEFAULT_OPUS_MODEL", None)
    os.environ.pop("ANTHROPIC_DEFAULT_OPUS_MODEL_SUPPORTED_CAPABILITIES", None)

    # Initially should be False
    res1 = modelo_soporta_thinking(model)
    print(f"Initial check for {model}: {res1}")

    # Set env
    os.environ["ANTHROPIC_DEFAULT_OPUS_MODEL"] = model
    os.environ["ANTHROPIC_DEFAULT_OPUS_MODEL_SUPPORTED_CAPABILITIES"] = "thinking,tools"

    # Should still be False due to cache (TTL 5s)
    res2_cached = modelo_soporta_thinking(model)
    print(f"Check IMMEDIATELY after setting env (should be cached False): {res2_cached}")

    print("Waiting 6 seconds for TTL to expire...")
    time.sleep(6)

    # Should now be True
    res2_new = modelo_soporta_thinking(model)
    print(f"Check after TTL wait: {res2_new}")

    assert res1 is False
    assert res2_cached is False
    assert res2_new is True
    print("Optimization Verification SUCCESS: TTL Cache working as expected.")

if __name__ == "__main__":
    test_thinking_env_with_ttl_cache()
