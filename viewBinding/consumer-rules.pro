#for viewbinding delegates
-keep,allowoptimization,allowobfuscation class * implements androidx.viewbinding.ViewBinding {
public static *** bind(android.view.View);
public static *** inflate(android.view.LayoutInflater);
public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}