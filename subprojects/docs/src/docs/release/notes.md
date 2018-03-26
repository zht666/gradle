## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Create tasks with constructor arguments

You can now create a task and pass values to its constructor.
In order to pass values to the constructor, you must annotate the relevant constructor with `@javax.inject.Inject`.
Given the following `Task` class:

    public class CustomTask extends DefaultTask {
        private final String message;
        private final int number;

        @Inject
        CustomTask(String message, int number) {
            this.message = message;
            this.number = number;
        }

        @TaskAction
        public void doSomething() { }
    }

You can then create the task using the Groovy DSL syntax and the `constructorArgs` key.

    task myTask(type: CustomTask, constructorArgs: ['hello', 42])

You can use the alternative syntax for defining tasks, passing the constructor arguments at the end.

    tasks.create('myTask', CustomTask, 'hello', 42)

Using the Kotlin DSL, you can pass constructor arguments using the reified extension function on the `tasks` `TaskContainer`.

    tasks.create<CustomTask>("myTask", "hello", 42)

More details are available in the [user guide](userguide/more_about_tasks.html#sec:passing_arguments_to_a_task_constructor)

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Deprecated methods on `FileCollection`

- `FileCollection.add()` is now deprecated. Use `ConfigurableFileCollection.from()` instead. You can create a `ConfigurableFileCollection` via `Project.files()`.

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Florian Nègre](https://github.com/fnegre) Fix distribution plugin documentation (gradle/gradle#4880)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
