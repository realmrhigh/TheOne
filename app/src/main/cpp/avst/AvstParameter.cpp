#include "AvstParameter.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <cstdio>

namespace avst {

AvstParameter::AvstParameter(const ParameterInfo& info) 
    : info_(info), value_(info.defaultValue), modulation_(0.0) {
    // Validate parameter info
    if (info_.minValue >= info_.maxValue) {
        throw std::invalid_argument("Parameter min value must be less than max value");
    }
    
    // Set default value within range
    double clampedDefault = std::clamp(info_.defaultValue, info_.minValue, info_.maxValue);
    value_.store(clampedDefault);
}

double AvstParameter::getValue() const {
    double baseValue = value_.load();
    double mod = modulation_.load();
    
    // Apply modulation
    double modulatedValue = baseValue + mod;
    
    // Clamp to parameter range
    return std::clamp(modulatedValue, info_.minValue, info_.maxValue);
}

void AvstParameter::setValue(double value) {
    // Clamp to valid range
    double clampedValue = std::clamp(value, info_.minValue, info_.maxValue);
    value_.store(clampedValue);
}

void AvstParameter::setNormalizedValue(double normalizedValue) {
    // Clamp normalized value to 0.0-1.0
    double clampedNormalized = std::clamp(normalizedValue, 0.0, 1.0);
    
    // Convert to parameter range
    double rawValue = denormalizeValue(clampedNormalized);
    setValue(rawValue);
}

double AvstParameter::getNormalizedValue() const {
    return normalizeValue(getValue());
}

std::string AvstParameter::getDisplayValue() const {
    double currentValue = getValue();
    
    if (info_.valueToString) {
        return info_.valueToString(currentValue);
    }
    
    // Default formatting
    if (info_.type == ParameterType::BOOL) {
        return currentValue > 0.5 ? "On" : "Off";
    } else if (info_.type == ParameterType::INT) {
        return std::to_string(static_cast<int>(currentValue));
    } else {
        // Format float with specified precision
        char buffer[64];
        snprintf(buffer, sizeof(buffer), "%.*f", info_.precision, currentValue);
        return std::string(buffer) + " " + info_.units;
    }
}

void AvstParameter::setDisplayValue(const std::string& displayValue) {
    if (info_.stringToValue) {
        double value = info_.stringToValue(displayValue);
        setValue(value);
    } else {
        // Try to parse as number
        try {
            double value = std::stod(displayValue);
            setValue(value);
        } catch (const std::exception&) {
            // Ignore invalid input
        }
    }
}

void AvstParameter::addModulation(double modulationAmount) {
    modulation_.store(modulationAmount);
}

void AvstParameter::clearModulation() {
    modulation_.store(0.0);
}

double AvstParameter::normalizeValue(double rawValue) const {
    if (info_.hints & static_cast<uint32_t>(ParameterHint::LOGARITHMIC)) {
        // Logarithmic scaling for frequency/gain parameters
        double logMin = std::log(std::max(info_.minValue, 0.001));
        double logMax = std::log(info_.maxValue);
        double logValue = std::log(std::max(rawValue, 0.001));
        return (logValue - logMin) / (logMax - logMin);
    } else {
        // Linear scaling
        return (rawValue - info_.minValue) / (info_.maxValue - info_.minValue);
    }
}

double AvstParameter::denormalizeValue(double normalizedValue) const {
    if (info_.hints & static_cast<uint32_t>(ParameterHint::LOGARITHMIC)) {
        // Logarithmic scaling
        double logMin = std::log(std::max(info_.minValue, 0.001));
        double logMax = std::log(info_.maxValue);
        double logValue = logMin + normalizedValue * (logMax - logMin);
        return std::exp(logValue);
    } else {
        // Linear scaling
        return info_.minValue + normalizedValue * (info_.maxValue - info_.minValue);
    }
}

} // namespace avst
