# Android with Kotlin

To get started, create an `Activity` in your brand new project and go to _Code > Convert Java to Kotlin_. Then, on your next build, Android Studio will ask you to configure Kotlin for the app module. Done !

## Libraries and tips

### Application context

To access a `Context` statically, create an application class extending `Application`, like this:

```
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
```

In the `manifest.xml`, add a `name` property to the `<application>`:

```xml
<application 
    android:name=".App" ... >
</application>
```

That's it ! Now, you can access the context statically using `App.appContext`.

### Timber

[Timber](https://github.com/JakeWharton/timber) is a logging utility. Add the dependency to the `build.gradle`:

```yaml
implementation 'com.jakewharton.timber:timber:4.6.0'
```

You need to setup the library once (the easiest is to do it in the `onCreate` of the application):

```java
Timber.plant(DebugTree())
```

Example usages:

```kotlin
Timber.d("a debug message")
Timber.d("a debug message with placeholders: %b, %s", aBool, aString)
Timber.d(myException) // with stacktrace
```

### Kovenant

[Kovenant](https://github.com/mplatvoet/kovenant) is a library bringing promises to kotlin. By default, the callbacks (_success_, _failure_) will run on one of the kovenant background threads. To easily have callbacks running on the main thread, also import _Kovenant UI_.

**Dependencies**:

```yaml
implementation 'nl.komponents.kovenant:kovenant-android:3.3.0'
implementation 'nl.komponents.kovenant:kovenant-ui:3.3.0'
```

**Setup**: it is good practice to explicitly start/stop kovenant. In the `App`, use:

```kotlin
override fun onCreate() {
  super.onCreate()
  startKovenant()
}

override fun onTerminate() {
  super.onTerminate()
  stopKovenant()
}
```

**Creating promises**: create a `Deferred` instance, define the tasks and return the promise. Here is a basic example:

```kotlin
// do something with arg and return a boolean on success, an exception on failure.
fun doSomethingInBackground(arg: String): Promise<Boolean, Exception> {
    // define the deferred object
    val deferred = deferred<Boolean, Exception>()
    
    task {
        // this block will run on a background thread
        // do something
        val argInt = arg.toInt() // might throw a NumberFormatException
        // resolve the promise with a result, which will call the
        // success exception
        deferred.resolve(argInt > 10)
    } fail {
        // an exception occurred: reject the promise, which will call the
        // fail callback
        // note: it -> the argument, here an exception
        deferred.reject(it)
    }
  
    // return the promise
    return deferred.promise
}
```

**Using promises**: a promise has three important types of callbacks: `success` called on `deferred.resolve`, `fail` called on `deferred.reject` and `always`, which runs whatever happens.

```kotlin
doSomethingInBackground(myString).success {
  Timber.d("success on thread %s: result = %b", Thread.currentThread(), it)
} fail {
    Timber.d("failure on thread %s: exception = %s", Thread.currentThread(), it)
} always {
    Timber.d("I am always running! (thread %s)", Thread.currentThread())
}
```

Here, the different callbacks will run inside a background thread created by kovenant. If you need to interact with the view inside the callback, use `successUi`, `failUi` and `alwaysUi` (if you don't use the view, please avoid it, since it impacts the performances).

**Limiting the number of background threads**: by default, kovenant will create a pool of threads, so multiple promise tasks can run concurrently. To avoid that, create a custom context in the `App` :

```kotlin
override fun onCreate() {
  super.onCreate()
  // limit background threads to one to avoid concurrency 
  Kovenant.context {
    workerContext.dispatcher = buildDispatcher {
      name = "Kovenant worker thread"
      concurrentTasks = 1
    }
  }
  startKovenant()
}
```

More info in the [kovenant documentation](http://kovenant.komponents.nl/).

## Kotlin exerimental extensions

To use them, add the following at the top of your `build.gradle`:

```yaml
apply plugin: 'org.jetbrains.kotlin.android.extensions'
androidExtensions {
    experimental = true
}
```

Then, the namespace `kotlinx` is made available, with so [many nice touches](https://kotlinlang.org/docs/tutorials/android-plugin.html) !

### ~~findViewById~~

Say you have a layout file called `activity_main.xml`. Inside are many fields with id, for example:

```xml
<CheckBox android:id="@+id/myCheckbox" ... />
<TextView android:id="@+id/someTextView" ... />
```

Inside your activity, no more `findViewById`. Instead, add:

```kotlin
// replace activity_main by your layout name
import kotlinx.android.synthetic.main.activity_main.*
```

you can then directly reference your views anywhere in your code ! For example: 

```kotlin
myCheckbox.isChecked = false
someTextView.text = "I love kotlin"
```

### Parcels

You can turnn any class into a `Parcelable`:

```kotlin
@Parcelize
data class User(var firstName: String, var lastName: String, var age: Int): Parcelable
```

Limitations:

* `@Parcelize` requires all serialized properties to be declared in the primary constructor. Android Extensions will issue a warning on each property with a backing field declared in the class body
* `@Parcelize` can't be applied if some of the primary constructor parameters are not properties.

## Coroutines

Coroutines are great to quickly create asynctasks or simply run a heavy function without blocking the UI. Personnally, I used [anko coroutines](https://github.com/Kotlin/anko/wiki/Anko-Coroutines) but it seems kotlinx just added [an implementation of their own](https://github.com/Kotlin/kotlinx.coroutines).

**Setup**: add the import in your `build.gradle`

```yaml
# for anko
implementation 'org.jetbrains.anko:anko-coroutines:$anko_version'
# for kotlinx
implementation 'org.jetbrains.anko:anko-coroutines:$kotlinx_version'
```

**Usage (anko)**: first, let execute a heavy function wihout blocking the UI.

```kotlin
fun loadAndShowData() {
	// Ref<T> uses the WeakReference under the hood
	val ref: Ref<MyActivity> = this.asReference()

	async(UI) {
	    val data = getData()	
	    // Use ref() instead of this@MyActivity to avoid memory leak
	    ref().showData()
	}
}
```

If you need something to run in a background thread instead, use the `bg` block construct:

```kotlin
async(UI) {
    val data: Deferred<Data> = bg {
	   // Runs in background
	   getData()
    }

    // This code is executed on the UI thread once the 
    // background thread has finished
    showData(data.await())
}
```

## Security

To make the screen not viewable in the recents apps (no thumbnail, blank screen), set the following flags in the activity `onCreate`:

```kotlin
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, 
                WindowManager.LayoutParams.FLAG_SECURE);
```

Currently, there is no way of customizing the thumbnail shown (i.e. screen always blank).

## Android databinding

* the ids `something_with_underscore` are automatically converted to `somethingWithUnderscore`  ([stackoverflow](https://stackoverflow.com/questions/37727600/cannot-refer-to-other-view-id-in-android-data-binding))
* use `@={}` for two-way, `@{}` for one way data binding 

## Other

* `@CallSuper` annotation 




# Android tips

### Ripple effect with custom background

As explained [here](https://stackoverflow.com/a/44161732/2667536), set your custom background in the backgroun and the ripple attribute on the foreground:

```xml
<Button 
	android:background="@drawable/custom_button_disable_fill"
    android:foreground="?android:attr/selectableItemBackground"
    android:clickable="true"
    ...
/>
```





## Android styles

### AlertDialog

If you are using the `android.app.AlertDialog`:

```xml
 <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
   ...
   <item name="android:alertDialogTheme">@style/AppTheme.AlertDialog</item>
</style>

<style name="AppTheme.AlertDialog" parent="Theme.AppCompat.Light.Dialog.Alert">
  <!--for this to work, set android:alertDialogTheme in AppTheme-->
  <!--color of the title and buttons-->
  <item name="android:textColor">@color/colorGreenyDark</item>
  <!-- background color -->
  <item name="android:background">@color/whity</item>
</style>
```

if you are using the AppCompat library, specify the theme directly in the constructor:

```kotlin
AlertDialog.Builder(activity, R.style.AppTheme_AlertDialog)
```

For more detailed styling, see https://qiita.com/granoeste/items/bc30c25caefe5ceb102b#stylesxml


TODO: 

* login again after some time in the background

  ​