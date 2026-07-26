# Script PowerShell para renombrar archivos de formato Jules a formato ISO-SAGE estándar
# Formato Jules: CLAW_2024_06_19_DESCRIPCION_V01.ext
# Formato ISO-SAGE: 2024-06-19_CLAW_DESCRIPCION_V01.ext

$ErrorActionPreference = "Stop"

Write-Host "Iniciando renombrado de archivos a formato ISO-SAGE..." -ForegroundColor Cyan
Write-Host ""

# Contadores
$renombrados = 0
$errores = 0
$omitidos = 0

# Función para convertir formato Jules a ISO-SAGE
function Convert-JulesToISO-SAGE {
    param(
        [string]$nombre
    )

    # Patrón Jules: CLAW_2024_06_19_DESCRIPCION_VXX.ext
    if ($nombre -match "^CLAW_(\d{4})_(\d{2})_(\d{2})_(.+)_V(\d+)(\..+)$") {
        $anio = $matches[1]
        $mes = $matches[2]
        $dia = $matches[3]
        $descripcion = $matches[4]
        $version = $matches[5]
        $extension = $matches[6]

        # Nuevo formato: AAAA-MM-DD_CLAW_DESCRIPCION_VXX.ext
        $nuevoNombre = "${anio}-${mes}-${dia}_CLAW_${descripcion}_V${version}${extension}"
        return $nuevoNombre
    }

    return $null
}

# Procesar archivos en 00_SOPORTE
Write-Host "Procesando 00_SOPORTE..." -ForegroundColor Yellow
if (Test-Path "00_SOPORTE") {
    Get-ChildItem -Path "00_SOPORTE" -File | ForEach-Object {
        $nuevoNombre = Convert-JulesToISO-SAGE -nombre $_.Name
        if ($nuevoNombre -and $nuevoNombre -ne $_.Name) {
            try {
                Rename-Item -Path $_.FullName -NewName $nuevoNombre
                Write-Host "  Renombrado: $($_.Name) -> $nuevoNombre" -ForegroundColor Green
                $renombrados++
            }
            catch {
                Write-Host "  ERROR: $($_.Name) - $($_.Exception.Message)" -ForegroundColor Red
                $errores++
            }
        }
        else {
            $omitidos++
        }
    }
}

# Procesar archivos en 01_SRC
Write-Host "Procesando 01_SRC..." -ForegroundColor Yellow
if (Test-Path "01_SRC") {
    Get-ChildItem -Path "01_SRC" -File -Recurse | ForEach-Object {
        $nuevoNombre = Convert-JulesToISO-SAGE -nombre $_.Name
        if ($nuevoNombre -and $nuevoNombre -ne $_.Name) {
            try {
                Rename-Item -Path $_.FullName -NewName $nuevoNombre
                Write-Host "  Renombrado: $($_.Name) -> $nuevoNombre" -ForegroundColor Green
                $renombrados++
            }
            catch {
                Write-Host "  ERROR: $($_.Name) - $($_.Exception.Message)" -ForegroundColor Red
                $errores++
            }
        }
        else {
            $omitidos++
        }
    }
}

# Procesar archivos en 02_TESTS
Write-Host "Procesando 02_TESTS..." -ForegroundColor Yellow
if (Test-Path "02_TESTS") {
    Get-ChildItem -Path "02_TESTS" -File -Recurse | ForEach-Object {
        $nuevoNombre = Convert-JulesToISO-SAGE -nombre $_.Name
        if ($nuevoNombre -and $nuevoNombre -ne $_.Name) {
            try {
                Rename-Item -Path $_.FullName -NewName $nuevoNombre
                Write-Host "  Renombrado: $($_.Name) -> $nuevoNombre" -ForegroundColor Green
                $renombrados++
            }
            catch {
                Write-Host "  ERROR: $($_.Name) - $($_.Exception.Message)" -ForegroundColor Red
                $errores++
            }
        }
        else {
            $omitidos++
        }
    }
}

# Procesar archivos en 03_DOCS
Write-Host "Procesando 03_DOCS..." -ForegroundColor Yellow
if (Test-Path "03_DOCS") {
    Get-ChildItem -Path "03_DOCS" -File -Recurse | ForEach-Object {
        $nuevoNombre = Convert-JulesToISO-SAGE -nombre $_.Name
        if ($nuevoNombre -and $nuevoNombre -ne $_.Name) {
            try {
                Rename-Item -Path $_.FullName -NewName $nuevoNombre
                Write-Host "  Renombrado: $($_.Name) -> $nuevoNombre" -ForegroundColor Green
                $renombrados++
            }
            catch {
                Write-Host "  ERROR: $($_.Name) - $($_.Exception.Message)" -ForegroundColor Red
                $errores++
            }
        }
        else {
            $omitidos++
        }
    }
}

# Procesar archivos en 04_ASSETS
Write-Host "Procesando 04_ASSETS..." -ForegroundColor Yellow
if (Test-Path "04_ASSETS") {
    Get-ChildItem -Path "04_ASSETS" -File -Recurse | ForEach-Object {
        $nuevoNombre = Convert-JulesToISO-SAGE -nombre $_.Name
        if ($nuevoNombre -and $nuevoNombre -ne $_.Name) {
            try {
                Rename-Item -Path $_.FullName -NewName $nuevoNombre
                Write-Host "  Renombrado: $($_.Name) -> $nuevoNombre" -ForegroundColor Green
                $renombrados++
            }
            catch {
                Write-Host "  ERROR: $($_.Name) - $($_.Exception.Message)" -ForegroundColor Red
                $errores++
            }
        }
        else {
            $omitidos++
        }
    }
}

# Resumen
Write-Host ""
Write-Host "=== RESUMEN ===" -ForegroundColor Cyan
Write-Host "Archivos renombrados: $renombrados" -ForegroundColor Green
Write-Host "Archivos omitidos (ya en formato correcto): $omitidos" -ForegroundColor Yellow
Write-Host "Errores: $errores" -ForegroundColor Red
Write-Host ""

if ($errores -eq 0) {
    Write-Host "✅ Renombrado completado exitosamente!" -ForegroundColor Green
}
else {
    Write-Host "⚠️  Renombrado completado con $errores errores" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Próximo paso: Actualizar referencias en código Python" -ForegroundColor Cyan
