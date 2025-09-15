// Copyright (C) 2025 nyxynx

#include <jni.h>
#include "native.h"
#include <cstring>
#include <string>
#include <android/log.h>
#include <fstream>
#include <xdelta3_wrapper.h>

#define LOG_TAG "DeltaPatcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void XDeltaPatchJNI::sendLogToCallback(JNIEnv* env, jobject logCallback, const std::string& message) {
    if (logCallback) {
        jclass callbackClass = env->GetObjectClass(logCallback);
        jmethodID onLogUpdate = env->GetMethodID(callbackClass, "onLogUpdate", "(Ljava/lang/String;)V");
        if (onLogUpdate) {
            jstring logMessage = env->NewStringUTF(message.c_str());
            env->CallVoidMethod(logCallback, onLogUpdate, logMessage);
            env->DeleteLocalRef(logMessage);
        }
    }
}

jint XDeltaPatchJNI::encode(JNIEnv *env, jstring originalPath, jstring modifiedPath, jstring outputPath,
                            jstring description, jobject logCallback, jboolean useChecksum,
                            jint compressionLevel, jint secondaryCompression, jint srcWindowSize) {

    const char* origPath = env->GetStringUTFChars(originalPath, nullptr);
    const char* modPath = env->GetStringUTFChars(modifiedPath, nullptr);
    const char* outPath = env->GetStringUTFChars(outputPath, nullptr);
    const char* desc = env->GetStringUTFChars(description, nullptr);

    try {
        // Create the XDeltaPatch with the output path where patch will be written
        XDeltaPatch patch(outPath, XDeltaPatch::Write);

        if (desc && strlen(desc) > 0) {
            patch.SetDescription(std::string(desc));
        }

        XDeltaConfig& config = patch.GetConfig();
        config.enableChecksum = static_cast<bool>(useChecksum);
        config.compressionLevel = static_cast<int>(compressionLevel);
        config.secondaryCompression = static_cast<int>(secondaryCompression);

        if (srcWindowSize == 0) {
            config.srcWindowSize = XDeltaConfig::SRC_WINDOW_SIZE_AUTO;
        } else {
            config.srcWindowSize = XDeltaConfig::SrcWindowSizes[srcWindowSize - 1];
        }


        std::string message;
        int result = patch.Encode(origPath, modPath, message);

        // Get xdelta3 messages and send to callback
        std::string allMessages = xd3_messages();
        if (!allMessages.empty()) {
            sendLogToCallback(env, logCallback, allMessages);
        }

        env->ReleaseStringUTFChars(originalPath, origPath);
        env->ReleaseStringUTFChars(modifiedPath, modPath);
        env->ReleaseStringUTFChars(outputPath, outPath);
        env->ReleaseStringUTFChars(description, desc);

        return result;

    } catch (const std::exception& e) {
        sendLogToCallback(env, logCallback, "Error: " + std::string(e.what()));

        env->ReleaseStringUTFChars(originalPath, origPath);
        env->ReleaseStringUTFChars(modifiedPath, modPath);
        env->ReleaseStringUTFChars(outputPath, outPath);
        env->ReleaseStringUTFChars(description, desc);

        return -1;
    }
}

jint XDeltaPatchJNI::decode(JNIEnv* env, jobject instance,
                            jstring originalPath, jstring outputPath,
                            jstring patchPath, jboolean useChecksum, jobject logCallback) {

    const char* origPath = env->GetStringUTFChars(originalPath, nullptr);
    const char* outPath = env->GetStringUTFChars(outputPath, nullptr);
    const char* patchP = env->GetStringUTFChars(patchPath, nullptr);

    try {
        XDeltaPatch patch(patchP, XDeltaPatch::Read);

        XDeltaConfig& config = patch.GetConfig();
        config.enableChecksum = static_cast<bool>(useChecksum);

        std::string message;
        int result = patch.Decode(origPath, outPath, message);

        // Get xdelta3 messages and send to callback
        std::string allMessages = xd3_messages();
        if (!allMessages.empty() && logCallback) {
            sendLogToCallback(env, logCallback, allMessages);
        }

        env->ReleaseStringUTFChars(originalPath, origPath);
        env->ReleaseStringUTFChars(outputPath, outPath);
        env->ReleaseStringUTFChars(patchPath, patchP);

        return result;

    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(originalPath, origPath);
        env->ReleaseStringUTFChars(outputPath, outPath);
        env->ReleaseStringUTFChars(patchPath, patchP);

        return -1;
    }
}

std::string XDeltaPatchJNI::getDescription(JNIEnv* env, jobject instance, jstring patchPath) {
    const char* patchP = env->GetStringUTFChars(patchPath, nullptr);

    try {
        XDeltaPatch patch(patchP, XDeltaPatch::Read);
        std::string desc = patch.GetDescription();

        env->ReleaseStringUTFChars(patchPath, patchP);
        return desc;

    } catch (const std::exception& e) {
        LOGE("Exception in getDescription: %s", e.what());
        env->ReleaseStringUTFChars(patchPath, patchP);
        return "";
    }
}

extern "C"
JNIEXPORT jint JNICALL Java_io_github_innixunix_deltapatcher_NativeLibrary_00024Companion_encode(
        JNIEnv* env, jobject thiz,
        jstring originalPath, jstring modifiedPath,
        jstring outputPath, jstring description,
        jobject logCallback, jboolean useChecksum,
        jint compressionLevel, jint secondaryCompression, jint srcWindowSize
) {
    return XDeltaPatchJNI::encode(env, originalPath, modifiedPath, outputPath, description,
                                  logCallback, useChecksum, compressionLevel,
                                  secondaryCompression, srcWindowSize);
}

extern "C"
JNIEXPORT jint JNICALL Java_io_github_innixunix_deltapatcher_NativeLibrary_00024Companion_decode(
    JNIEnv* env, jobject thiz,
    jstring originalPath, jstring outputPath, jstring patchPath, jboolean useChecksum, jobject logCallback
) {
    return XDeltaPatchJNI::decode(env, thiz, originalPath, outputPath, patchPath, useChecksum, logCallback);
}

extern "C"
JNIEXPORT jstring JNICALL Java_io_github_innixunix_deltapatcher_NativeLibrary_00024Companion_getDescription(
    JNIEnv* env, jobject thiz,
    jstring patchPath
) {
    std::string desc = XDeltaPatchJNI::getDescription(env, thiz, patchPath);
    return env->NewStringUTF(desc.c_str());
}