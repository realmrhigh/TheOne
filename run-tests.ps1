# PowerShell script to build and run C++ tests on Windows host
# Run this from the project root directory

Write-Host "Building C++ tests for host platform..." -ForegroundColor Green

# Create build directory for host builds
if (-not (Test-Path "build-host")) {
    New-Item -ItemType Directory -Path "build-host" | Out-Null
}

Set-Location "build-host"

# Configure CMake for host platform (not Android)
cmake ../app/src/main/cpp

# Build the tests
cmake --build . --config Release

# Run the tests
Write-Host "Running tests..." -ForegroundColor Green
ctest --output-on-failure

Set-Location ..
Write-Host "Test run complete!" -ForegroundColor Green
