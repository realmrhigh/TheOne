#pragma once

#include "AvstParameterContainer.h"
#include "AvstAudioIO.h"
#include <string>
#include <memory>

namespace avst {

// Plugin Types
enum class PluginType {
    INSTRUMENT,     // Synthesizers, samplers
    EFFECT,         // Audio processors  
    ANALYZER,       // Spectrum analyzers, meters
    CONTROLLER      // MIDI controllers, sequencers
};

// Plugin Categories (for organizing in UI)
enum class PluginCategory {
    SYNTHESIZER,
    SAMPLER, 
    FILTER,
    DELAY,
    REVERB,
    DISTORTION,
    MODULATION,
    DYNAMICS,
    UTILITY,
    CUSTOM
};

struct PluginInfo {
    std::string id;              // Unique identifier (reverse domain notation)
    std::string name;            // Display name
    std::string vendor;          // Developer/company name
    std::string version;         // Version string (e.g., "1.0.0")
    PluginType type;
    PluginCategory category;
    
    // Capabilities
    bool hasUI = true;           // Can display custom UI
    bool isSynth = false;        // Generates audio (vs processes it)
    bool acceptsMidi = false;    // Accepts MIDI input
    bool producesMidi = false;   // Generates MIDI output
    
    // Mobile-specific
    uint32_t cpuUsageEstimate = 50;    // 0-100 relative CPU usage
    uint32_t memoryUsageKB = 1024;     // Estimated memory usage
    bool supportsBackground = true;     // Can run when app backgrounded
};

// Audio processing context
struct ProcessContext {
    float** inputs;              // Input audio buffers
    float** outputs;             // Output audio buffers
    uint32_t numInputs;          // Number of input channels
    uint32_t numOutputs;         // Number of output channels
    uint32_t frameCount;         // Number of frames to process
    
    float sampleRate;            // Current sample rate
    double tempo = 120.0;        // Current tempo (BPM)
    double timePosition = 0.0;   // Position in seconds
    bool isPlaying = false;      // Transport state
    
    // Parameter automation
    std::vector<AvstParameterContainer::ParameterChange> parameterChanges;
};

// MIDI message (simplified)
struct MidiMessage {
    uint8_t status;              // Status byte
    uint8_t data1;               // First data byte  
    uint8_t data2;               // Second data byte
    uint32_t sampleOffset;       // When in the buffer this occurs
};

// Abstract base class for all AVST plugins
class IAvstPlugin {
public:
    virtual ~IAvstPlugin() = default;
    
    // === Plugin Information ===
    virtual PluginInfo getPluginInfo() const = 0;
    virtual AvstParameterContainer& getParameters() = 0;
    virtual const AvstParameterContainer& getParameters() const = 0;
    
    // === Audio Processing Setup ===
    virtual bool initialize(const AudioIOConfig& config) = 0;
    virtual void shutdown() = 0;
    
    // Called when audio format changes
    virtual bool setAudioIOConfig(const AudioIOConfig& config) = 0;
    virtual AudioIOConfig getAudioIOConfig() const = 0;
    
    // === Real-time Audio Processing ===
    virtual void processAudio(ProcessContext& context) = 0;
    
    // === MIDI Support (optional) ===
    virtual void processMidiMessage(const MidiMessage& message) {}
    virtual std::vector<MidiMessage> getMidiOutput() { return {}; }
    
    // === State Management ===
    virtual std::vector<uint8_t> saveState() const = 0;
    virtual bool loadState(const std::vector<uint8_t>& state) = 0;
    
    // === Preset Management ===
    virtual bool savePreset(const std::string& name, const std::string& filePath) const;
    virtual bool loadPreset(const std::string& filePath);
    virtual std::vector<std::string> getPresetList() const;
    
    // === UI Support ===
    virtual bool hasCustomUI() const;
    virtual void* createUI(void* parentWindow) { return nullptr; }
    virtual void destroyUI(void* uiHandle) {}
    virtual void updateUI() {}
    
    // === Mobile-specific ===
    virtual void onAppBackground() {}    // App going to background
    virtual void onAppForeground() {}    // App returning to foreground
    virtual void onLowMemory() {}        // System low memory warning
    
    // === Performance Monitoring ===
    virtual float getCpuUsage() const { return 0.0f; }
    virtual uint32_t getMemoryUsage() const { return 0; }
    
protected:
    // Helper for common parameter operations
    template<typename T>
    T getParameterValue(const std::string& paramId, T defaultValue = T{}) const {
        const auto* param = getParameters().getParameter(paramId);
        if (param) {
            return static_cast<T>(param->getValue());
        }
        return defaultValue;
    }
    
    void setParameterValue(const std::string& paramId, double value) {
        auto* param = getParameters().getParameter(paramId);
        if (param) {
            param->setValue(value);
        }
    }
};

// Plugin factory function type
using PluginCreateFunction = std::unique_ptr<IAvstPlugin>(*)();

// Plugin registration macro for easy plugin creation
#define AVST_PLUGIN_EXPORT(ClassName) \
    std::unique_ptr<avst::IAvstPlugin> createAvstPlugin() { \
        return std::make_unique<ClassName>(); \
    } \
    avst::PluginInfo getAvstPluginInfo() { \
        auto plugin = std::make_unique<ClassName>(); \
        return plugin->getPluginInfo(); \
    }

} // namespace avst
