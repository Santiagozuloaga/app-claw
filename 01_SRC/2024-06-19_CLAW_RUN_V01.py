#!/usr/bin/env python3
import sys
from pathlib import Path
import importlib

# Set paths for P.A.R.A. structure
project_root = Path(__file__).parent.absolute()
sys.path.insert(0, str(project_root / "01_SRC"))
sys.path.insert(0, str(project_root / "00_SOPORTE"))

# Import core using correct ISO-SAGE nomenclature (date-first)
# Since the filename starts with numbers and has hyphens, we must use importlib
core = importlib.import_module("2024-06-19_CLAW_CORE_V01")

if __name__ == "__main__":
    core.main()
