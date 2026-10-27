#include <jni.h>
#include <string.h>
#include <stdio.h>

static JavaVM* dalvikJavaVMPtr;

static JavaVM* runtimeJavaVMPtr;
static JNIEnv* runtimeJNIEnvPtr_GRAPHICS;
static JNIEnv* runtimeJNIEnvPtr_INPUT;
jclass class_CTCScreen;
jmethodID method_GetRGB;

jclass class_CTCAndroidInput;
jmethodID method_ReceiveInput;

jclass class_MainActivity;
jmethodID method_OpenLink;
jmethodID method_OpenPath;
jmethodID method_QuerySystemClipboard;
jmethodID method_PutClipboardData;

jclass class_Frame;
jclass class_Rectangle;
jclass class_CTCClipboard = NULL;
jmethodID constructor_Rectangle;
jmethodID method_GetFrames;
jmethodID method_GetBounds;
jmethodID method_SetBounds;
jmethodID method_SystemClipboardDataReceived = NULL;

static void clearPendingException(JNIEnv *env) {
    if (env != NULL && (*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

static jclass findGlobalClassWithFallback(JNIEnv *env, const char *primary, const char *fallback) {
    if (env == NULL || primary == NULL) return NULL;
    jclass localClass = (*env)->FindClass(env, primary);
    if (localClass == NULL || (*env)->ExceptionCheck(env)) {
        clearPendingException(env);
        if (fallback == NULL) return NULL;
        localClass = (*env)->FindClass(env, fallback);
    }
    if (localClass == NULL || (*env)->ExceptionCheck(env)) {
        clearPendingException(env);
        return NULL;
    }
    jclass globalClass = (jclass)(*env)->NewGlobalRef(env, localClass);
    (*env)->DeleteLocalRef(env, localClass);
    if (globalClass == NULL || (*env)->ExceptionCheck(env)) {
        clearPendingException(env);
        if (globalClass != NULL) (*env)->DeleteGlobalRef(env, globalClass);
        return NULL;
    }
    return globalClass;
}

jfieldID field_x;
jfieldID field_y;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    if (dalvikJavaVMPtr == NULL) {
        // Save the Android VM only after all required callback methods have been resolved.
        JNIEnv *env = NULL;
        if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4) != JNI_OK || env == NULL) return JNI_ERR;
        jclass hostClass = (*env)->FindClass(env, "com/sally/launcher/core/engine/AwtBridgeHost");
        if (hostClass == NULL || (*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            return JNI_ERR;
        }
        class_MainActivity = (*env)->NewGlobalRef(env, hostClass);
        (*env)->DeleteLocalRef(env, hostClass);
        if (class_MainActivity == NULL) return JNI_ERR;
        method_OpenLink = (*env)->GetStaticMethodID(env, class_MainActivity, "openLink", "(Ljava/lang/String;)V");
        method_OpenPath = (*env)->GetStaticMethodID(env, class_MainActivity, "openPath", "(Ljava/lang/String;)V");
        method_QuerySystemClipboard = (*env)->GetStaticMethodID(env, class_MainActivity, "querySystemClipboard", "()V");
        method_PutClipboardData = (*env)->GetStaticMethodID(env, class_MainActivity, "putClipboardData", "(Ljava/lang/String;Ljava/lang/String;)V");
        if ((*env)->ExceptionCheck(env) || method_OpenLink == NULL || method_OpenPath == NULL ||
            method_QuerySystemClipboard == NULL || method_PutClipboardData == NULL) {
            (*env)->ExceptionClear(env);
            (*env)->DeleteGlobalRef(env, class_MainActivity);
            class_MainActivity = NULL;
            return JNI_ERR;
        }
        dalvikJavaVMPtr = vm;
    } else if (dalvikJavaVMPtr != vm) {
        runtimeJavaVMPtr = vm;
    }

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSendData(JNIEnv* env, jclass clazz, jint type, jint i1, jint i2, jint i3, jint i4) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else if ((*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, (void **)&runtimeJNIEnvPtr_INPUT, NULL) != JNI_OK) {
            runtimeJNIEnvPtr_INPUT = NULL;
            return;
        }
    }
    if (runtimeJNIEnvPtr_INPUT == NULL) return;

    if (method_ReceiveInput == NULL) {
        class_CTCAndroidInput = findGlobalClassWithFallback(
            runtimeJNIEnvPtr_INPUT,
            "net/java/openjdk/cacio/ctc/CTCAndroidInput",
            "com/github/caciocavallosilano/cacio/ctc/CTCAndroidInput"
        );
        if (class_CTCAndroidInput == NULL) return;
        method_ReceiveInput = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(
            runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, "receiveData", "(IIIII)V"
        );
        if (method_ReceiveInput == NULL || (*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT)) {
            clearPendingException(runtimeJNIEnvPtr_INPUT);
            (*runtimeJNIEnvPtr_INPUT)->DeleteGlobalRef(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput);
            class_CTCAndroidInput = NULL;
            method_ReceiveInput = NULL;
            return;
        }
    }
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
        runtimeJNIEnvPtr_INPUT,
        class_CTCAndroidInput,
        method_ReceiveInput,
        type, i1, i2, i3, i4
    );
}

// TODO: check for memory leaks
// int printed = 0;
int threadAttached = 0;
JNIEXPORT jintArray JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrame(JNIEnv* env, jclass clazz /*, jobject canvas, jint width, jint height */) {
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return NULL;
        } else if ((*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, (void **)&runtimeJNIEnvPtr_GRAPHICS, NULL) != JNI_OK) {
            runtimeJNIEnvPtr_GRAPHICS = NULL;
            return NULL;
        }
    }
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) return NULL;

    int *rgbArray;
    jintArray jreRgbArray, androidRgbArray;
  
    if (method_GetRGB == NULL) {
        class_CTCScreen = findGlobalClassWithFallback(
            runtimeJNIEnvPtr_GRAPHICS,
            "net/java/openjdk/cacio/ctc/CTCScreen",
            "com/github/caciocavallosilano/cacio/ctc/CTCScreen"
        );
        if (class_CTCScreen == NULL) return NULL;
        method_GetRGB = (*runtimeJNIEnvPtr_GRAPHICS)->GetStaticMethodID(
            runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen, "getCurrentScreenRGB", "()[I"
        );
        if (method_GetRGB == NULL || (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS)) {
            clearPendingException(runtimeJNIEnvPtr_GRAPHICS);
            (*runtimeJNIEnvPtr_GRAPHICS)->DeleteGlobalRef(runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen);
            class_CTCScreen = NULL;
            method_GetRGB = NULL;
            return NULL;
        }
    }
    jreRgbArray = (jintArray) (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticObjectMethod(
        runtimeJNIEnvPtr_GRAPHICS,
        class_CTCScreen,
        method_GetRGB
    );
    if (jreRgbArray == NULL || (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS)) {
        clearPendingException(runtimeJNIEnvPtr_GRAPHICS);
        return NULL;
    }

    // Copy the JVM framebuffer to Android without aborting on allocation failure.
    int arrayLength = (*runtimeJNIEnvPtr_GRAPHICS)->GetArrayLength(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
    rgbArray = (*runtimeJNIEnvPtr_GRAPHICS)->GetIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, 0);
    if (rgbArray == NULL || (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS)) {
        clearPendingException(runtimeJNIEnvPtr_GRAPHICS);
        (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
        return NULL;
    }
    androidRgbArray = (*env)->NewIntArray(env, arrayLength);
    if (androidRgbArray == NULL || (*env)->ExceptionCheck(env)) {
        clearPendingException(env);
        (*runtimeJNIEnvPtr_GRAPHICS)->ReleaseIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, rgbArray, JNI_ABORT);
        (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, androidRgbArray, 0, arrayLength, rgbArray);
    (*runtimeJNIEnvPtr_GRAPHICS)->ReleaseIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, rgbArray, JNI_ABORT);
    (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
    if ((*env)->ExceptionCheck(env)) {
        clearPendingException(env);
        (*env)->DeleteLocalRef(env, androidRgbArray);
        return NULL;
    }
    return androidRgbArray;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    if (dalvikJavaVMPtr == NULL || class_MainActivity == NULL || method_QuerySystemClipboard == NULL) return;
    JNIEnv *dalvikEnv = NULL; char detachable = 0;
    jint status = (*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, (void **)&dalvikEnv, NULL) != JNI_OK) return;
        detachable = 1;
    } else if (status != JNI_OK || dalvikEnv == NULL) return;
    if(method_SystemClipboardDataReceived == NULL) {
        class_CTCClipboard = (*env)->NewGlobalRef(env, clazz);
        if (class_CTCClipboard != NULL) {
            method_SystemClipboardDataReceived = (*env)->GetStaticMethodID(env, clazz, "systemClipboardDataReceived", "(Ljava/lang/String;Ljava/lang/String;)V");
        }
        if (method_SystemClipboardDataReceived == NULL || (*env)->ExceptionCheck(env)) {
            clearPendingException(env);
            if (class_CTCClipboard != NULL) (*env)->DeleteGlobalRef(env, class_CTCClipboard);
            class_CTCClipboard = NULL;
            method_SystemClipboardDataReceived = NULL;
            if (detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
            return;
        }
    }
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_QuerySystemClipboard);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    if (dalvikJavaVMPtr == NULL || class_MainActivity == NULL || method_PutClipboardData == NULL) return;
    JNIEnv *dalvikEnv = NULL; char detachable = 0;
    jint status = (*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, (void **)&dalvikEnv, NULL) != JNI_OK) return;
        detachable = 1;
    } else if (status != JNI_OK || dalvikEnv == NULL) return;

    const char* dataChars = clipboardData != NULL ? (*env)->GetStringUTFChars(env, clipboardData, NULL) : NULL;
    const char* mimeChars = clipboardDataMime != NULL ? (*env)->GetStringUTFChars(env, clipboardDataMime, NULL) : NULL;
    if ((clipboardData != NULL && dataChars == NULL) || (clipboardDataMime != NULL && mimeChars == NULL)) {
        if (dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
        if (mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
        if (detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
        return;
    }
    jstring data = dataChars != NULL ? (*dalvikEnv)->NewStringUTF(dalvikEnv, dataChars) : NULL;
    jstring mime = mimeChars != NULL ? (*dalvikEnv)->NewStringUTF(dalvikEnv, mimeChars) : NULL;
    if ((dataChars != NULL && data == NULL) || (mimeChars != NULL && mime == NULL) || (*dalvikEnv)->ExceptionCheck(dalvikEnv)) {
        clearPendingException(dalvikEnv);
    } else {
        (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_PutClipboardData, data, mime);
        clearPendingException(dalvikEnv);
    }
    if (data != NULL) (*dalvikEnv)->DeleteLocalRef(dalvikEnv, data);
    if (mime != NULL) (*dalvikEnv)->DeleteLocalRef(dalvikEnv, mime);
    if (dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    if (mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(env, clazz);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(env, clazz, clipboardData, clipboardDataMime);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openFile(JNIEnv *env, jclass clazz, jstring filePath) {
    if (dalvikJavaVMPtr == NULL || class_MainActivity == NULL || method_OpenPath == NULL || filePath == NULL) return;
    JNIEnv *dalvikEnv = NULL; char detachable = 0;
    jint status = (*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, (void **)&dalvikEnv, NULL) != JNI_OK) return;
        detachable = 1;
    } else if (status != JNI_OK || dalvikEnv == NULL) return;
    const char* stringChars = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (stringChars != NULL) {
        jstring path = (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars);
        if (path != NULL && !(*dalvikEnv)->ExceptionCheck(dalvikEnv)) {
            (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenPath, path);
            clearPendingException(dalvikEnv);
            (*dalvikEnv)->DeleteLocalRef(dalvikEnv, path);
        } else clearPendingException(dalvikEnv);
        (*env)->ReleaseStringUTFChars(env, filePath, stringChars);
    }
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openUri(JNIEnv *env, jclass clazz, jstring uri) {
    if (dalvikJavaVMPtr == NULL || class_MainActivity == NULL || method_OpenLink == NULL || uri == NULL) return;
    JNIEnv *dalvikEnv = NULL; char detachable = 0;
    jint status = (*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, (void **)&dalvikEnv, NULL) != JNI_OK) return;
        detachable = 1;
    } else if (status != JNI_OK || dalvikEnv == NULL) return;
    const char* stringChars = (*env)->GetStringUTFChars(env, uri, NULL);
    if (stringChars != NULL) {
        jstring link = (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars);
        if (link != NULL && !(*dalvikEnv)->ExceptionCheck(dalvikEnv)) {
            (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenLink, link);
            clearPendingException(dalvikEnv);
            (*dalvikEnv)->DeleteLocalRef(dalvikEnv, link);
        } else clearPendingException(dalvikEnv);
        (*env)->ReleaseStringUTFChars(env, uri, stringChars);
    }
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeClipboardReceived(JNIEnv *env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    if(method_SystemClipboardDataReceived == NULL || class_CTCClipboard == NULL) return;
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL || (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, (void **)&runtimeJNIEnvPtr_INPUT, NULL) != JNI_OK) {
            runtimeJNIEnvPtr_INPUT = NULL;
            return;
        }
    }
    const char* dataChars = clipboardData != NULL ? (*env)->GetStringUTFChars(env, clipboardData, NULL) : NULL;
    const char* mimeChars = clipboardDataMime != NULL ? (*env)->GetStringUTFChars(env, clipboardDataMime, NULL) : NULL;
    if ((clipboardData != NULL && dataChars == NULL) || (clipboardDataMime != NULL && mimeChars == NULL)) {
        if (dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
        if (mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
        return;
    }
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCClipboard, method_SystemClipboardDataReceived,
                                                    clipboardData != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, dataChars) : NULL,
                                                    clipboardDataMime != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, mimeChars) : NULL);
    if(dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    if(mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_AWTInputBridge_nativeMoveWindow(JNIEnv *env, jclass clazz, jint xoff, jint yoff) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else if ((*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, (void **)&runtimeJNIEnvPtr_INPUT, NULL) != JNI_OK) {
            runtimeJNIEnvPtr_INPUT = NULL;
            return;
        }
    }
    if (runtimeJNIEnvPtr_INPUT == NULL) return;
    if(field_y == NULL) {
        class_Frame = findGlobalClassWithFallback(runtimeJNIEnvPtr_INPUT, "java/awt/Frame", NULL);
        class_Rectangle = findGlobalClassWithFallback(runtimeJNIEnvPtr_INPUT, "java/awt/Rectangle", NULL);
        if (class_Frame == NULL || class_Rectangle == NULL) return;
        method_GetFrames = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getFrames", "()[Ljava/awt/Frame;");
        method_GetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getBounds", "(Ljava/awt/Rectangle;)Ljava/awt/Rectangle;");
        method_SetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "setBounds", "(Ljava/awt/Rectangle;)V");
        constructor_Rectangle = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "<init>", "()V");
        field_x = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "x", "I");
        field_y = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "y", "I");
        if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) || method_GetFrames == NULL ||
            method_GetBounds == NULL || method_SetBounds == NULL || constructor_Rectangle == NULL ||
            field_x == NULL || field_y == NULL) {
            clearPendingException(runtimeJNIEnvPtr_INPUT);
            return;
        }
    }
    jobject rectangle = (*runtimeJNIEnvPtr_INPUT)->NewObject(runtimeJNIEnvPtr_INPUT, class_Rectangle, constructor_Rectangle);
    jobjectArray frames = (jobjectArray)(*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(runtimeJNIEnvPtr_INPUT, class_Frame, method_GetFrames);
    if (rectangle == NULL || frames == NULL || (*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT)) {
        clearPendingException(runtimeJNIEnvPtr_INPUT);
        if (rectangle != NULL) (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, rectangle);
        if (frames != NULL) (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frames);
        return;
    }
    for(jsize i = 0; i < (*runtimeJNIEnvPtr_INPUT)->GetArrayLength(runtimeJNIEnvPtr_INPUT, frames); i++) {
        jobject frame = (*runtimeJNIEnvPtr_INPUT)->GetObjectArrayElement(runtimeJNIEnvPtr_INPUT, frames, i);
        (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(runtimeJNIEnvPtr_INPUT, frame, method_GetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_x, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_x) + xoff);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_y, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_y) + yoff);
        (*runtimeJNIEnvPtr_INPUT)->CallVoidMethod(runtimeJNIEnvPtr_INPUT, frame, method_SetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frame);
    }
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, rectangle);
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frames);
}
