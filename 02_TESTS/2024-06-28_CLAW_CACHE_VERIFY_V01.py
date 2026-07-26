import os
import time
import sys
from pathlib import Path

# Add 01_SRC to path
sys.path.append(str(Path(__file__).parent.parent / "01_SRC"))

from thinking import modelo_soporta_thinking
from providers import _model_supports_thinking

def test_thinking_cache():
    model = "claude-fake-model"
    env_var = "ANTHROPIC_DEFAULT_SONNET_MODEL"
    caps_var = "ANTHROPIC_DEFAULT_SONNET_MODEL_SUPPORTED_CAPABILITIES"

    os.environ[env_var] = model
    os.environ[caps_var] = "tools" # No thinking

    # Wait for TTL if any
    time.sleep(1)

    print(f"Initial state: {model} support thinking? {modelo_soporta_thinking(model)}")

    # Change env
    os.environ[caps_var] = "thinking,tools"

    # Wait more than 5s if thinking.py uses TTL 5s
    print("Waiting 6 seconds for TTL cache...")
    time.sleep(6)

    res = modelo_soporta_thinking(model)
    print(f"After env change: {model} support thinking? {res}")

    if res == True:
        print("SUCCESS: thinking.py picked up environment change.")
        return True
    else:
        print("FAILURE: thinking.py did NOT pick up environment change.")
        return False

def test_providers_cache():
    model = "claude-fake-model-2"
    env_var = "ANTHROPIC_DEFAULT_OPUS_MODEL"
    caps_var = "ANTHROPIC_DEFAULT_OPUS_MODEL_SUPPORTED_CAPABILITIES"

    os.environ[env_var] = model
    os.environ[caps_var] = "tools"

    print(f"Initial state (providers): {model} support thinking? {_model_supports_thinking(model)}")

    # Change env
    os.environ[caps_var] = "thinking,tools"

    res = _model_supports_thinking(model)
    print(f"After env change (providers): {model} support thinking? {res}")

    if res == True:
        print("SUCCESS: providers.py picked up environment change.")
        return True
    else:
        print("FAILURE: providers.py did NOT pick up environment change.")
        return False

if __name__ == "__main__":
    s1 = test_thinking_cache()
    print("-" * 20)
    s2 = test_providers_cache()
    if not (s1 and s2):
        sys.exit(1)
