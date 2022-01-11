-keep,allowoptimization public class com.github.ppaszkiewicz.tools.toolbox.** { public *; protected *;}
-keep,allowoptimization class com.github.ppaszkiewicz.tools.toolbox.** { public *;}
-keep,allowoptimization interface com.github.ppaszkiewicz.tools.toolbox.** { public *;}
-keep,allowoptimization enum com.github.ppaszkiewicz.tools.toolbox.** { public *;}

#for viewbinding delegates
-keep,allowoptimization,allowobfuscation class * implements androidx.viewbinding.ViewBinding {
public static *** bind(android.view.View);
public static *** inflate(android.view.LayoutInflater);
public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}