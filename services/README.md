[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Release](https://jitpack.io/v/PPaszkiewicz/Tools.svg)](https://jitpack.io/#PPaszkiewicz/Tools)

Services
=======

ServiceConnection implementation that's lifecycle aware and exposes service callbacks in more
understandable way than default.

Import in **build.gradle** or **build.gradle.kts**:
```gradle    
implementation("com.github.PPaszkiewicz.Tools:services:$version")
```

## Basic Usage
1. Have your service extend `DirectBindService.Impl` and create factory in companion object:
 ```kotlin
class YourService : DirectBindService.Impl() {
    companion object {
        val connectionFactory = DirectBindService.ConnectionFactory<YourService>()
    }

    /** service implementation **/
}
 ```
2. Inside your activity create lifecycle aware connection using factory:
```kotlin
class YourActivity : AppCompatActivity() {
    val connection = YourService.connectionFactory.lifecycle(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // inject basic listeners
        connection.onConnect = {
            Log.d("Demo", "Connected to service : $it")
        }
        // rest of onCreate
    }
}
```
This will automatically bind to your service for as long as activity is alive.

## Other service classes

If you cannot inherit from `DirectBindService.Impl` you have to create `DirectBinder` and parse binding
intent manually:

 ```kotlin
class YourService : NotificationListenerService(), DirectBindService {
    companion object {
        val connectionFactory = DirectBindService.ConnectionFactory<YourService>()
    }

    val mBinder = DirectBinder(this)

    override fun onBind(intent: Intent): IBinder? {
        if (intent.action == DirectBindService.BIND_DIRECT_ACTION)
            return mBinder
        return super.onBind(intent)
    }

    /** service implementation **/
}
 ```

Usage in activity remains the same.

## Third party services

When you cannot access and modify the service class to bind to (for example when using AIDL) use
`RemoteBindService.ConnectionFactory`:

```kotlin
// create static factory object to use across project
object MyAidlConnectionFactory : RemoteBindService.ConnectionFactory<MyAidlInterface>() {
    override fun createBindingIntent(context: Context): Intent {
        val aidlService = ComponentName("com.sample.sample", "com.sample.sample.MyAidlService")
        return Intent(MyAidlInterface.BIND_ACTION).setComponent(aidlService)
    }

    override fun transformBinder(name: ComponentName, binder: IBinder): MyAidlInterface {
        return MyAidlInterface.Stub.asInterface(binder)
    }
}
```

Then usage in activity is same as before, we just use this connection factory:

```kotlin
class YourActivity : AppCompatActivity() {
    val connection = MyAidlConnectionFactory.lifecycle(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // inject basic listeners
        connection.onConnect = {
            Log.d("Demo", "Connected to service : $it")
        }
        // rest of onCreate
    }
}
```

## Classes

**BindServiceConnection** - abstract base for service connection implementations.

**BindServiceConnectionCallbacks** - interface for connection callbacks.

**BindServiceConnectionLambdas** - callback interface that defines hot pluggable lambdas instead of methods.

**DirectBindService** - abstract marker interface for services that can be bound to directly (within same process).
Its companion object contains smart connection factory methods.

**DirectBindService.Impl** - default service implementation that can be extended if possible.

**DirectBindService.LifecycleImpl** - default lifecycle service implementation that can be extended if possible.

**DirectBindService.ConnectionFactory** - connection factory for specific direct bind service class. This can be created or inherited by
that services companion object for convenience.

**LingeringService** - service that auto-starts self to persist for a while after `unbind()`.
Its companion object contains required connection factory methods.

**LingeringService.ConnectionFactory** - connection factory for specific lingering service class. This can be created or inherited by
that services companion object for convenience.

**RemoteBindService.ConnectionFactory** - connection factory that can be supplied custom intent and binder
handling behavior.

## License
Copyright 2019-2021 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
