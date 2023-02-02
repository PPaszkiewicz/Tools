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

### Transition

Few common transitions and utilities.

### Views

**StableTextView**, **StableChronometer** - text views that do not invalidate layout when text is changed,
useful to prevent excessive layout requests (for example in RecyclerView).

**ImmersiveConstraintLayout** - holds common logic for keeping layout immersive.

**SaveChildStateLayouts** - logic for containing save state of all children within their parent.  Prevents collision with duplicate view ids within the layout.

**TileRenderLinearLayout** - layout that tiles out multiple drawing of its content for "loading" effect.

**orientation** - contains a "compass" and "guides" that help building layouts with selectable orientation.

### ViewBinding
`ViewBinding` delegates for activity, fragments and views that are lifecycle aware so they doen't require any `onDestroyView` overrides.
Sample:

    // inside fragment:
    val binding by viewBinding<MainActivityBinding>()

**ViewBinding** - core methods for delegates.

**ViewTags** - delegates for view tags.

**Reflection** - delegates that are based on reflection so they can work just with class name.

## License
Copyright 2021 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
