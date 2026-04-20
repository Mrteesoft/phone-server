#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pty.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <string>
#include <vector>

namespace {

void throwIOException(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/io/IOException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char* raw = env->GetStringUTFChars(value, nullptr);
    if (raw == nullptr) {
        return "";
    }

    std::string result(raw);
    env->ReleaseStringUTFChars(value, raw);
    return result;
}

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) {
        return result;
    }

    const jsize count = env->GetArrayLength(array);
    result.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; index += 1) {
        auto* value = static_cast<jstring>(env->GetObjectArrayElement(array, index));
        result.emplace_back(toString(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<char*> toCStringVector(std::vector<std::string>& source) {
    std::vector<char*> result;
    result.reserve(source.size() + 1);
    for (std::string& item : source) {
        result.push_back(item.data());
    }
    result.push_back(nullptr);
    return result;
}

int exitCodeFromStatus(int status) {
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return 1;
}

}  // namespace

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_phoneserver_mobile_runtime_PtyBridge_nativeStart(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray argvArray,
        jobjectArray environmentArray,
        jstring workingDirectory,
        jint columns,
        jint rows) {
    std::vector<std::string> argv = toStringVector(env, argvArray);
    if (argv.empty()) {
        throwIOException(env, "PTY start requires at least one argv entry.");
        return nullptr;
    }

    std::vector<std::string> environment = toStringVector(env, environmentArray);
    const std::string cwd = toString(env, workingDirectory);
    std::vector<char*> argvPointers = toCStringVector(argv);

    struct winsize windowSize {};
    windowSize.ws_col = static_cast<unsigned short>(columns > 0 ? columns : 120);
    windowSize.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 40);

    int masterFd = -1;
    const pid_t pid = forkpty(&masterFd, nullptr, nullptr, &windowSize);
    if (pid < 0) {
        throwIOException(env, std::string("forkpty failed: ") + strerror(errno));
        return nullptr;
    }

    if (pid == 0) {
        if (!cwd.empty()) {
            chdir(cwd.c_str());
        }

        clearenv();
        for (const std::string& entry : environment) {
            putenv(strdup(entry.c_str()));
        }

        execvp(argvPointers[0], argvPointers.data());

        dprintf(STDERR_FILENO, "execvp failed: %s\n", strerror(errno));
        _exit(127);
    }

    jint values[2];
    values[0] = static_cast<jint>(pid);
    values[1] = static_cast<jint>(masterFd);

    jintArray result = env->NewIntArray(2);
    if (result == nullptr) {
        close(masterFd);
        kill(pid, SIGKILL);
        return nullptr;
    }
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_phoneserver_mobile_runtime_PtyBridge_nativeWaitFor(
        JNIEnv* env,
        jobject /* this */,
        jint pidValue) {
    int status = 0;
    const pid_t pid = static_cast<pid_t>(pidValue);
    const pid_t waited = waitpid(pid, &status, 0);
    if (waited < 0) {
        throwIOException(env, std::string("waitpid failed: ") + strerror(errno));
        return 1;
    }
    return exitCodeFromStatus(status);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_phoneserver_mobile_runtime_PtyBridge_nativeSignal(
        JNIEnv* env,
        jobject /* this */,
        jint pidValue,
        jint signalValue) {
    const pid_t pid = static_cast<pid_t>(pidValue);
    const int result = kill(pid, signalValue);
    if (result == 0) {
        return JNI_TRUE;
    }
    if (errno == ESRCH) {
        return JNI_FALSE;
    }

    throwIOException(env, std::string("kill failed: ") + strerror(errno));
    return JNI_FALSE;
}
