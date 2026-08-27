# ONNX Runtime reaches its native layer through JNI, so the classes the .so calls back into
# cannot be renamed or removed. R8 full mode makes this sharper than usual: a -keep on a
# class no longer implies keeping its members, so the members are named explicitly.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# OkHttp / Okio, for shake-to-report.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
