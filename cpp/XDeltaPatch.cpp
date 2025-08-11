// Copyright (C) 2025 Innixunix
// Adapted for Android

#include "XDeltaPatch.h"
#include "utils/base64.h"
#include <xdelta3_wrapper.h>

#include <vector>
#include <sstream>
#include <iostream>
#include <fstream>
#include <cstring>

size_t XDeltaPatch::DecodeVarLength(std::ifstream& file) {
    size_t length = 0;
    uint8_t byte = 0;
    do {
        length <<= 7;
        file.read(reinterpret_cast<char*>(&byte), 1);
        length |= byte & 0x7F;
    } while (byte & 0x80);
    
    return length;
}

std::vector<std::string> XDeltaPatch::SplitMessageByLine(const std::string& str) {
    std::vector<std::string> tokens;
    
    std::stringstream ss(str);
    std::string token;
    while (std::getline(ss, token, '\n')) {
        tokens.push_back(token);
    }
    
    return tokens;
}

const int XDeltaConfig::SrcWindowSizes[] = {
    8 << 20, 
    16 << 20,
    32 << 20,
    64 << 20,
    128 << 20,
    256 << 20,
    512 << 20,
    1024 << 20
};

const char* XDeltaConfig::SecondaryCompressions[] = {
    "lzma",
    "djw",
    "fgk",
    "none"
};

XDeltaPatch::XDeltaPatch(const char* input, PatchMode mode) {
    if (!input)
        return;
        
    patchName = input;
    
    if (mode != Read)
        return;
        
    DecodeDescription();
}

void XDeltaPatch::DecodeDescription() {
    std::ifstream file(patchName, std::ios::binary);
    
    if (!file.is_open())
        return;
    
    uint8_t magic[3];
    file.read(reinterpret_cast<char*>(magic), 3);
    
    if (magic[0] != 0xD6 || magic[1] != 0xC3 || magic[2] != 0xC4) {
        return;
    }
    
    uint8_t version = 0;
    file.read(reinterpret_cast<char*>(&version), 1);
    
    if (version != 0)
        return;
    
    uint8_t flags = 0;
    file.read(reinterpret_cast<char*>(&flags), 1);
    
    if (!(flags & 0x04)) {
        return;
    }
    
    if (flags & 0x01) {
        file.seekg(1, std::ios::cur);
    }
    
    if (flags & 0x02) {
        size_t length = DecodeVarLength(file);
        
        file.seekg(length, std::ios::cur);
    }
    
    size_t length = DecodeVarLength(file);
    
    if (length < 2)
        return;
    
    char* temp = new char[length + 1];
    file.read(temp, length);
    temp[length] = 0;
    
    if (temp[0] != '^' || temp[1] != '*') {
        delete[] temp;
        return;
    }
    
    std::string tempDesc(temp);
    delete[] temp;
    
    std::string part = tempDesc.substr(2);
    std::string buf = base64_decode(part);
    
    description = buf;
    
    size_t pos = 0;
    while ((pos = description.find("\r\n", pos)) != std::string::npos) {
        description.replace(pos, 2, "\n");
        pos += 1;
    }
    pos = 0;
    while ((pos = description.find("\r", pos)) != std::string::npos) {
        description.replace(pos, 1, "\n");
        pos += 1;
    }
}

std::string XDeltaPatch::EncodeDescription() {
    std::string result;
    if (description.empty()) {
        result = "Created with Delta Patcher.";
    } else {
        result = "^*";
        result += base64_encode(reinterpret_cast<const unsigned char*>(description.c_str()), description.length());
    }
    return result;
}

std::string XDeltaPatch::GetDescription() {
    return description;
}

void XDeltaPatch::SetDescription(const std::string& description) {
    this->description = description;
}

int XDeltaPatch::Process(const std::string& original, const std::string& out, const std::string& patch, std::string& message, bool encode) {
    std::vector<std::string> params = MakeCommand(original, out, patch, encode);
    
    int ret = xd3_main_exec(params);
    std::string messages = xd3_messages();
    std::vector<std::string> outArray = SplitMessageByLine(messages);
    
    if (outArray.size() > 0)
        message = outArray[0];
    else
        message = messages;
        
    return ret;
}

int XDeltaPatch::Decode(const char* original, const char* out, std::string& message) {
    return Process(original, out, patchName, message, false);
}

int XDeltaPatch::Encode(const char* original, const char* modified, std::string& message) {
    return Process(original, modified, patchName, message, true);
}



std::vector<std::string> XDeltaPatch::MakeCommand(const std::string& original, const std::string& out, const std::string& patch, bool encode) {
    std::vector<std::string> params;
    
    if (encode) {
        params.push_back("-e");
    } else {
        params.push_back("-d");
    }
    
    if (!config.enableChecksum)
        params.push_back("-n");
    
    if (config.overwriteOutput)
        params.push_back("-f");
    
    if (encode) {
        std::string compression_flag = "-";
        compression_flag += std::to_string(config.compressionLevel);
        params.push_back(compression_flag);
        
        params.push_back("-S");
        params.push_back(XDeltaConfig::SecondaryCompressions[config.secondaryCompression]);
        
        if (config.srcWindowSize != XDeltaConfig::SRC_WINDOW_SIZE_AUTO) {
            params.push_back("-B");
            params.push_back(std::to_string(config.srcWindowSize));
        }
        
        std::string base64 = EncodeDescription();
        
        std::string desc_str = "-A=";
        desc_str += base64;
        params.push_back(desc_str);
    }
    
    if (encode) {
        params.push_back("-s");
        params.push_back(original);
        params.push_back(out);
        params.push_back(patch);
    } else {
        params.push_back("-s");
        params.push_back(original);
        params.push_back(patch);
        params.push_back(out);
    }
    
    return params;
}

void XDeltaPatch::SetConfig(const XDeltaConfig& config) {
    this->config = config;
}

XDeltaConfig& XDeltaPatch::GetConfig() {
    return config;
}