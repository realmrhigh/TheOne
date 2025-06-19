#pragma once

#include <string>
#include <functional>
#include <atomic>

namespace avst {

// Parameter Types
enum class ParameterType {
    FLOAT,      // Continuous values (0.0-1.0 normalized)
    INT,        // Discrete values  
    BOOL,       // On/Off switches
    CHOICE,     // Dropdown selections
    STRING      // Text input
};

// Parameter Categories
enum class ParameterCategory {
    AUDIO_IO,   // Audio routing and format
    CONTROL,    // User-controllable parameters
    STATE,      // Internal plugin state
    MODULATION  // LFO/Envelope targets
};

// Touch-optimized parameter hints
enum class ParameterHint : uint32_t {
    NONE = 0,
    LOGARITHMIC = 1 << 0,      // Frequency, gain parameters
    BIPOLAR = 1 << 1,          // -1.0 to +1.0 range
    GESTURE_XY = 1 << 2,       // 2D touch control
    GESTURE_CIRCULAR = 1 << 3,  // Rotary gesture
    AUTOMATABLE = 1 << 4,      // Can be automated
    REALTIME_SAFE = 1 << 5     // Safe for audio thread
};

// Bitwise operators for ParameterHint
inline ParameterHint operator|(ParameterHint a, ParameterHint b) {
    return static_cast<ParameterHint>(static_cast<uint32_t>(a) | static_cast<uint32_t>(b));
}

inline ParameterHint operator&(ParameterHint a, ParameterHint b) {
    return static_cast<ParameterHint>(static_cast<uint32_t>(a) & static_cast<uint32_t>(b));
}

struct ParameterInfo {
    std::string id;                    // Unique identifier
    std::string displayName;           // User-visible name
    std::string units;                 // "Hz", "dB", "%", etc.
    ParameterType type;
    ParameterCategory category;
    uint32_t hints;                    // Bitfield of ParameterHint
    
    // Range information
    double minValue = 0.0;
    double maxValue = 1.0;
    double defaultValue = 0.0;
    double stepSize = 0.0;             // 0.0 = continuous
    
    // Display formatting
    int precision = 2;                 // Decimal places
    std::function<std::string(double)> valueToString;
    std::function<double(const std::string&)> stringToValue;
};

class AvstParameter {
public:
    AvstParameter(const ParameterInfo& info);
    
    // Thread-safe parameter access
    double getValue() const;
    void setValue(double value);
    
    // Automation support
    void setNormalizedValue(double normalizedValue);
    double getNormalizedValue() const;
    
    // UI support  
    std::string getDisplayValue() const;
    void setDisplayValue(const std::string& displayValue);
    
    // Parameter info
    const ParameterInfo& getInfo() const { return info_; }
    
    // Modulation support (for LFOs, envelopes)
    void addModulation(double modulationAmount);
    void clearModulation();
    
private:
    ParameterInfo info_;
    std::atomic<double> value_;
    std::atomic<double> modulation_;
    
    double normalizeValue(double rawValue) const;
    double denormalizeValue(double normalizedValue) const;
};

} // namespace avst
