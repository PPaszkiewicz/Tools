[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Release](https://jitpack.io/v/PPaszkiewicz/Tools.svg)](https://jitpack.io/#PPaszkiewicz/Tools)

Tools
=====

My personal Kotlin based tools. 

To import ensure you have ```maven { url `jitpack.io` }``` repository in your project level **build.gradle**.

### Services

ServiceConnection implementation that's lifecycle aware and exposes service callbacks in more
understandable way than default.

Import in **build.gradle** or **build.gradle.kts**:
```gradle
implementation("com.github.PPaszkiewicz.Tools:services:$version")
```
### Toolbox

Mixed bag of kotlin utils. Most files are standalone so they can be copied into a project.

Import in **build.gradle** or **build.gradle.kts**:
```gradle    
implementation("com.github.PPaszkiewicz.Tools:toolbox:$version")
```
### ViewBinding

`ViewBinding` delegates and extensions.

Import in **build.gradle** or **build.gradle.kts**:
```gradle    
implementation("com.github.PPaszkiewicz.Tools:viewBinding:$version")
```
### Demo

Demo app for other packages.


## License
Copyright 2019-2023 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
