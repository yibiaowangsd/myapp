# Exact optional references from the pinned Huawei SDKs, not application classes.
# OEM APIs are guarded by the SDK and are absent from the public Android SDK.
-dontwarn android.telephony.HwTelephonyManager
-dontwarn com.huawei.android.os.BuildEx$VERSION
-dontwarn com.huawei.libcore.io.ExternalStorageFile
-dontwarn com.huawei.libcore.io.ExternalStorageFileInputStream
-dontwarn com.huawei.libcore.io.ExternalStorageFileOutputStream
-dontwarn com.huawei.libcore.io.ExternalStorageRandomAccessFile
# The optional standalone HiAnalytics library is not integrated by this app.
-dontwarn com.huawei.hianalytics.process.HiAnalyticsConfig$Builder
-dontwarn com.huawei.hianalytics.process.HiAnalyticsConfig
-dontwarn com.huawei.hianalytics.process.HiAnalyticsInstance$Builder
-dontwarn com.huawei.hianalytics.process.HiAnalyticsInstance
-dontwarn com.huawei.hianalytics.process.HiAnalyticsManager
-dontwarn com.huawei.hianalytics.util.HiAnalyticTools
# security-encrypt 1.2.1.304 checks the optional BC path and catches linkage
# failure, retaining java.security.SecureRandom. App KeyVault is unchanged.
-dontwarn org.bouncycastle.crypto.BlockCipher
-dontwarn org.bouncycastle.crypto.engines.AESEngine
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandom
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandomBuilder
