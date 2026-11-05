# 미니게임 - 만능 룰렛 & 골프 복불복 R8 / ProGuard 최적화 및 보호 규칙
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# AGP 9.0+ R8 Full Mode 대응: Google Mobile Ads 전이 의존성(WorkManager / Room / Startup) 리플렉션 생성자 보존
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    *;
}
-keep class * implements androidx.startup.Initializer {
    <init>();
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
