# Publishing the source

This directory is ready for a new GitHub repository. Review the name and repository visibility, then run:

```sh
git init -b main
git add .
git status --short
git commit -m "Initial Mavrolume source release"
git remote add origin <your-repository-url>
git push -u origin main
```

The repository contains the Gradle wrapper, source, tests, build workflow, MIT license and upstream attribution. It does not contain a signing key, local SDK configuration, build output or APK. Keep production signing credentials outside Git. Run a current trademark search before public release; a web search alone does not establish name availability.
