#pragma once

#include "AvstParameter.h"
#include <unordered_map>
#include <vector>
#include <memory>

namespace avst {

class AvstParameterContainer {
public:
    // Parameter registration (during plugin initialization)
    void registerParameter(std::unique_ptr<AvstParameter> parameter);
    
    // Parameter access by ID
    AvstParameter* getParameter(const std::string& id);
    const AvstParameter* getParameter(const std::string& id) const;
    
    // Parameter access by index (for automation)
    AvstParameter* getParameter(size_t index);
    size_t getParameterCount() const;
    
    // Bulk operations
    std::vector<ParameterInfo> getAllParameterInfo() const;
    void setParameterValues(const std::unordered_map<std::string, double>& values);
    std::unordered_map<std::string, double> getParameterValues() const;
    
    // Categories
    std::vector<AvstParameter*> getParametersByCategory(ParameterCategory category);
    
    // Automation support
    struct ParameterChange {
        size_t parameterIndex;
        double normalizedValue;
        uint32_t sampleOffset;  // When in the buffer to apply
    };
    
    void processParameterChanges(const std::vector<ParameterChange>& changes);

private:
    std::vector<std::unique_ptr<AvstParameter>> parameters_;
    std::unordered_map<std::string, size_t> parameterIdToIndex_;
};

} // namespace avst
