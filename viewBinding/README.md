[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Release](https://jitpack.io/v/PPaszkiewicz/Tools.svg)](https://jitpack.io/#PPaszkiewicz/Tools)

ViewBinding
=======
`ViewBinding` delegates for activity, fragments and views that are lifecycle aware so they don't require any `onDestroyView` overrides.

**Fragments:**
 ```kotlin
class MyFragment : Fragment(R.layout.my_fragment) {
    val binding by viewBinding<MyFragmentBinding>()
    //...
}
```

**Activity:**
 ```kotlin
 class MyActivity : AppCompatActivity() {
     val binding by viewBinding<MyActivityBinding>()
     //...
 }
 ```
 
 **DialogFragment:**
  ```kotlin
 class MyDialogFragment : DialogFragment() {
     val binding by dialogViewBinding<MyDialogFragmentBinding>()
     
     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
         return AlertDialog.Builder(requireContext(), theme)
             .setTitle("Dialog title")
             .setPositiveButton(android.R.string.ok) { _, _ -> }
             .setView(binding) {
                 textView1.text = "Hello World!"
             }.create()
     }
 }
 ```
 
## License
Copyright 2021-2023 Paweł Paszkiewicz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
