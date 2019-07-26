[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Release](https://jitpack.io/v/PPaszkiewicz/Tools.svg)](https://jitpack.io/#User/Repo)

Toolbox
=======
Utility classes / packages.

Most of them are copy-pasteable because they don't have any 
external dependencies. If they require another file it's specified in 
file header.

To import everything, add to **build.gradle** or **build.gradle.kts**:
```gradle    
implementation("com.github.PPaszkiewicz:Tools:toolbox:1.0.0")
```

### DownloadManager
Utilities for querying download progress from systems DownloadManager.

Available as standalone **DownloadProgressObserver** or wrapped with **LiveData**.

### Extensions
Some extensions including delegates for storing/writing values in PreferenceManager.

### Reflection
**IFieldReflector** - interface and utilities for reflecting fields and methods as simple delegates. See demo/tests for sample.

### Service
**DirectBindService** - abstract base for services that can be bound to directly (within same process).

**DirectServiceConnection** - connection implementation that can handle *bind()* and *unbind()* calls with lifecycle.

**LingeringService** - service that auto-starts self to persist for a while after *unbind()*.

**LingeringServiceConnection** - connection for **LingeringService**.

### Transition

Few common transitions and utilities.

### Views

**FixedSizeTextView**, **FixedSizeChronometer** - text views that do not invalidate layout when text is changed.

**ImmersiveConstraintLayout** - holds common logic for keeping layout immersive.

## License
Copyright 2019 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.