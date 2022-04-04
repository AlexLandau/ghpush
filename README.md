# ghpush

A command-line tool for pushing commits to GitHub as stacks of dependent pull requests. Currently in alpha state:
useful, but incomplete.

## Why stack pull requests (PRs)?

Let's say you have three changes you want to make to your repo's `main` branch that can each stand alone, but affect the
same code. There are three ways you can deal with this in GitHub:

1. Create three unrelated PRs targeting `main`, get them reviewed separately, and merge them in. This can result in
   merge conflicts that require extra cycles of work and attention. Worse still, these may be semantic merge conflicts
   that don't conflict in git, but cause compilation failures or other breaks after merging. This also may not be an
   option depending on how the changes are related -- for example, the first change may refactor code to create a method
   that the second change needs.
2. Put the three changes in the same PR. This results in a larger PR that is harder to review. This can lower the
   quality of the code review, and it can also prevent one logical part of the change from merging if a different part
   is rejected or delayed.
3. Create three PRs, with the later changes using the earlier changes as target branches. When the first PR is merged
   and its branch is deleted, the second PR's target branch will automatically switch to `main`. Depending on how CI
   is configured, this may not require CI to be rerun, so the three PRs can be merged in quick succession when ready.
   The downside here is the extra work needed to make this happen: Naming extra branches, setting the target branches
   when making PRs, and pushing in ways that keep branches up-to-date.

The `ghpush` CLI makes the third option easy. A single command handles all the necessary branch management and PR
configuration work.

## How to use

You can make the changes you want on any local branch. Make one commit per PR you want to make. Run `ghpush`, and it
will guide you through adding `gh-branch:` tags to each commit. Then, it will push your changes to GitHub and create a
PR for each change.

When you want to modify commits on this branch, the recommended approach is an interactive rebase (`git rebase -i`).
Modify the commits as desired, and when the rebase is done, rerun `ghpush` to update all the PRs at once.

### On gh-branch tags

These tags are used so that when you modify a commit with `git commit --amend` or `git rebase -i`, `ghpush` will
recognize the commit and modify the existing PR instead of creating a new one. The value of the tag is the branch name
that will be used on GitHub. It doesn't have to be a local branch name.

You can add these to your commit messages manually, but you don't have to. Running `ghpush` on an untagged commit will
prompt you for a gh-branch value for any commit that lacks one. Typing `a` at this prompt will autogenerate a branch
name based on the commit title.

See also the `ghpush.prefix` config option.

### Command-line arguments

| Argument                     | Effect                                                                                   |
|------------------------------|------------------------------------------------------------------------------------------|
| `-h` or `--help`             | Display a help message.                                                                  |
| `-v` or `--version`          | Display the current version of `ghpush`.                                                 |
| `-f` or `--force`            | Push your changes even if the branches have been updated upstream since you last pushed. |
| `--onto=some-release-branch` | Push your changes onto the named release branch instead of the repo's default branch.    |

### Configuration options

`ghpush` piggybacks on `git config` for its configuration options. This means you can use all of `git config`'s commands
to configure these options either globally or in specific repos. Run `git help config` for more information on how to
use it.

Quick example:

```shell
# Use your email name as your prefix across all repos
git config --global --add ghpush.prefix email
```

* `ghpush.prefix`: Adds a prefix to `gh-branch` names created using `ghpush`. This can be helpful to, e.g., prefix all
  branches you make with a particular ID to avoid conflicts with other users' branch names. For example, if
  `ghpush.prefix` is `foo`, and you type in `bar` as the branch name, the commit will end up with `gh-branch: foo/bar`.
  There are two special values for this feature:
  * `email`: If this is chosen, the prefix will be the first part (before the `@`) of your email address as defined by
    `user.email` in the git config.
  * `username`: If this is chosen, the prefix will be your GitHub username, as reported by `gh`.

## Installing ghpush

To use `ghpush`, you must have both `git` and `gh` (the GitHub command line tool) installed.

TODO: Fill in here

## Opinions and limitations

Currently, ghpush requires you to have one commit per PR. If someone makes an addition to a PR, you will have to squash
that change into your commit if you want to include it in a subsequent push.

TODO: Fill in here

## Comparisons with similar tools

TODO: Fill in here
