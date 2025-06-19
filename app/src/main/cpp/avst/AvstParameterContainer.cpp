#include "AvstParameterContainer.h"
#include <stdexcept>

namespace avst {

void AvstParameterContainer::registerParameter(std::unique_ptr<AvstParameter> parameter) {
    if (!parameter) {
        throw std::invalid_argument("Cannot register null parameter");
    }
    
    const std::string& id = parameter->getInfo().id;
    
    // Check for duplicate IDs
    if (parameterIdToIndex_.find(id) != parameterIdToIndex_.end()) {
        throw std::invalid_argument("Parameter ID already registered: " + id);
    }
    
    // Register the parameter
    size_t index = parameters_.size();
    parameterIdToIndex_[id] = index;
    parameters_.push_back(std::move(parameter));
}

AvstParameter* AvstParameterContainer::getParameter(const std::string& id) {
    auto it = parameterIdToIndex_.find(id);
    if (it != parameterIdToIndex_.end()) {
        return parameters_[it->second].get();
    }
    return nullptr;
}

const AvstParameter* AvstParameterContainer::getParameter(const std::string& id) const {
    auto it = parameterIdToIndex_.find(id);
    if (it != parameterIdToIndex_.end()) {
        return parameters_[it->second].get();
    }
    return nullptr;
}

AvstParameter* AvstParameterContainer::getParameter(size_t index) {
    if (index < parameters_.size()) {
        return parameters_[index].get();
    }
    return nullptr;
}

size_t AvstParameterContainer::getParameterCount() const {
    return parameters_.size();
}

std::vector<ParameterInfo> AvstParameterContainer::getAllParameterInfo() const {
    std::vector<ParameterInfo> info;
    info.reserve(parameters_.size());
    
    for (const auto& param : parameters_) {
        info.push_back(param->getInfo());
    }
    
    return info;
}

void AvstParameterContainer::setParameterValues(const std::unordered_map<std::string, double>& values) {
    for (const auto& [id, value] : values) {
        AvstParameter* param = getParameter(id);
        if (param) {
            param->setValue(value);
        }
    }
}

std::unordered_map<std::string, double> AvstParameterContainer::getParameterValues() const {
    std::unordered_map<std::string, double> values;
    
    for (const auto& param : parameters_) {
        values[param->getInfo().id] = param->getValue();
    }
    
    return values;
}

std::vector<AvstParameter*> AvstParameterContainer::getParametersByCategory(ParameterCategory category) {
    std::vector<AvstParameter*> categoryParams;
    
    for (const auto& param : parameters_) {
        if (param->getInfo().category == category) {
            categoryParams.push_back(param.get());
        }
    }
    
    return categoryParams;
}

void AvstParameterContainer::processParameterChanges(const std::vector<ParameterChange>& changes) {
    for (const auto& change : changes) {
        if (change.parameterIndex < parameters_.size()) {
            // Apply parameter change at the specified sample offset
            // Note: In a real implementation, you'd queue these changes
            // and apply them at the correct sample position during audio processing
            parameters_[change.parameterIndex]->setNormalizedValue(change.normalizedValue);
        }
    }
}

} // namespace avst
