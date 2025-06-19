#include "IAvstPlugin.h"
#include <fstream>
#include <sstream>

namespace avst {

bool IAvstPlugin::savePreset(const std::string& name, const std::string& filePath) const {
    try {
        std::ofstream file(filePath);
        if (!file.is_open()) {
            return false;
        }
        
        // Simple key-value format for now (we can upgrade to JSON later)
        file << "name=" << name << "\n";
        file << "version=" << getPluginInfo().version << "\n";
        file << "pluginId=" << getPluginInfo().id << "\n";
        
        // Save all parameter values
        auto paramValues = getParameters().getParameterValues();
        for (const auto& [id, value] : paramValues) {
            file << "param." << id << "=" << value << "\n";
        }
        
        return true;
    } catch (const std::exception&) {
        return false;
    }
}

bool IAvstPlugin::loadPreset(const std::string& filePath) {
    try {
        std::ifstream file(filePath);
        if (!file.is_open()) {
            return false;
        }
        
        std::string line;
        std::string pluginId;
        std::unordered_map<std::string, double> paramValues;
        
        while (std::getline(file, line)) {
            size_t equalPos = line.find('=');
            if (equalPos == std::string::npos) continue;
            
            std::string key = line.substr(0, equalPos);
            std::string value = line.substr(equalPos + 1);
            
            if (key == "pluginId") {
                pluginId = value;
            } else if (key.substr(0, 6) == "param.") {
                std::string paramId = key.substr(6);
                paramValues[paramId] = std::stod(value);
            }
        }
        
        // Verify this preset is for this plugin
        if (pluginId != getPluginInfo().id) {
            return false;
        }
          // Load parameter values
        const_cast<AvstParameterContainer&>(getParameters()).setParameterValues(paramValues);
        return true;
        
    } catch (const std::exception&) {
        return false;
    }
}

std::vector<std::string> IAvstPlugin::getPresetList() const {
    // This would typically scan a presets directory
    // For now, return empty list
    return {};
}

bool IAvstPlugin::hasCustomUI() const {
    return getPluginInfo().hasUI;
}

} // namespace avst
