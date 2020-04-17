[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Release](https://jitpack.io/v/PPaszkiewicz/Tools.svg)](https://jitpack.io/#PPaszkiewicz/Tools)

Toolbox
=======
Utility classes / packages.

Most of them are copy-pasteable because they don't have any 
external dependencies. If they require another file it's specified in 
file header.

To import everything, add to **build.gradle** or **build.gradle.kts**:
```gradle    
implementation("com.github.PPaszkiewicz.Tools:toolbox:$version")
```

### Delegate
Various delegates and their factories.

**Context** - lazy context delegates useful for declaring some delegates in fragments.

**Fragment** - delegates for creating/finding existing fragments.

    // inside activity or fragment:
    val myFragment by fragments<MyFragment>()

**Preferences** - delegates for exposing preference values.

    // inside activity or fragment:
    val userPreference by preferences().boolean("key", false)

**ViewBinding** - delegate for `ViewBinding` (Android Studio 3.6+) that's lifecycle aware so it doesn't require any `onDestroyView` override.

    // inside fragment:
    val binding by viewBinding<MainActivityBinding>()

### DownloadManager
Utilities for querying download progress from systems DownloadManager.

Available as standalone `DownloadProgressObserver` or wrapped with `LiveData`.

### Extensions
Various extensions relating to whatever is in the file name.

### RecyclerView

**GravityGridLayoutManager** - grid layout manager that applies gravity to items instead of putting them at top-start.

**NestedWrapLayoutManager** - linear layout manager that can be efficiently used within scrolling parent. Limited to
1 item view type and item heights have to be uniform regardless of their content.

### Reflection
**KMirror** - interface and utilities for reflecting fields and methods as simple delegates. See demo/tests for sample.

### Service
**BindServiceConnection** - abstract base for service connection implementations.

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

### Transition

Few common transitions and utilities.

### Views

**FixedSizeTextView**, **FixedSizeChronometer** - text views that do not invalidate layout when text is changed.

**ImmersiveConstraintLayout** - holds common logic for keeping layout immersive.

**SaveChildStateLayouts** - logic for containing save state of all children within their parent.  Prevents collision with duplicate view ids within the layout.

## License
Copyright 2019 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
