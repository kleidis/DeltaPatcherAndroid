// Copyright (C) 2025 Innixunix

#pragma once

#include <jni.h>
#include <string>
#include "XDeltaPatch.h"

class XDeltaPatchJNI {
public:
    static jint encode(JNIEnv *env, jstring originalPath, jstring modifiedPath, jstring outputPath,
                       jstring description, jobject logCallback, jboolean useChecksum,
                       jint compressionLevel, jint secondaryCompression, jint srcWindowSize);

    static jint decode(JNIEnv* env, jobject instance,
                       jstring originalPath, jstring outputPath,
                       jstring patchPath, jboolean useChecksum, jobject logCallback);

    static std::string getDescription(JNIEnv* env, jobject instance,
                                      jstring patchPath);

private:
    static void sendLogToCallback(JNIEnv* env, jobject logCallback, const std::string& message);
};