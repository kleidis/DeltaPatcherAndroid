//Copyright (C) 2010+ Phoenix.

//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, version 2.0.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License 2.0 for more details.

//A copy of the GPL 2.0 should have been included with the program.
//If not, see http://www.gnu.org/licenses/

//Source code and contact information can be found at
//https://github.com/marco-calautti

//Copyright (C) 2025 nyxynx
//Adapted for Android

#ifndef __XDELTA_PATCH__
#define __XDELTA_PATCH__

#include <string>
#include <vector>
#include <fstream>

struct XDeltaConfig {
    static const int SECONDARY_COMP_LENGTH = 4;
    static const int SRC_WINDOW_SIZE_LENGTH = 8;

    static const int MIN_COMPRESSION_LEVEL = 0;
    static const int MAX_COMPRESSION_LEVEL = 9;
    static const int DEFAULT_COMPRESSION_LEVEL = 5;
    static const int DEFAULT_SECONDARY_COMPRESSION = SECONDARY_COMP_LENGTH - 1;
    static const int SRC_WINDOW_SIZE_AUTO = -1;

    static const int SrcWindowSizes[SRC_WINDOW_SIZE_LENGTH];
    static const char* SecondaryCompressions[SECONDARY_COMP_LENGTH];

    XDeltaConfig() {
        compressionLevel = DEFAULT_COMPRESSION_LEVEL;
        secondaryCompression = DEFAULT_SECONDARY_COMPRESSION;
        enableChecksum = true;
        overwriteOutput = true;
        srcWindowSize = SRC_WINDOW_SIZE_AUTO;
    }

    int compressionLevel;
    int secondaryCompression;
    bool enableChecksum;
    bool overwriteOutput;
    int srcWindowSize;
};

class XDeltaPatch {
public:
    enum PatchMode {
        Read,
        Write
    };

    /**
     * Creates a patch with default config from specified patch file
     */
    XDeltaPatch(const char* patchName, PatchMode mode = Read);
    XDeltaPatch() : XDeltaPatch(nullptr, PatchMode::Read) {}

    virtual ~XDeltaPatch() {}

    void SetConfig(const XDeltaConfig& config);
    XDeltaConfig& GetConfig();

    std::string GetDescription();
    void SetDescription(const std::string& description);

    int Decode(const char* original, const char* out, std::string& message);
    int Encode(const char* original, const char* modified, std::string& message);

private:
    std::string patchName;
    XDeltaConfig config;
    std::string description;

    void DecodeDescription();
    std::string EncodeDescription();

    int Process(const std::string& original, const std::string& out, const std::string& patch, std::string& message, bool encode);
    std::vector<std::string> MakeCommand(const std::string& original, const std::string& out, const std::string& patch, bool encode);

    size_t DecodeVarLength(std::ifstream& file);
    std::vector<std::string> SplitMessageByLine(const std::string& str);
};

#endif