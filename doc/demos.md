# Okapi demonstration

There is a shell script at `doc/demo-run.sh` which serves multiple purposes:

* Assist during development, to start a local instance of Okapi and send a
scripted suite of requests.
* Commence a '[tmux](https://tmux.github.io/)' terminal session and configure
separate window panes.
* If '[asciinema](https://asciinema.org/)' is available, then record the
session.
* Enable the preparation and saving of repeatable demonstrations.

Open a command-line terminal window (about 150 columns by 70 lines)
in the top-level 'okapi' directory.

Run the script `doc/demo-run.sh` to establish the session.
Use the `-a` option to record it.

In a separate terminal window, run a story script (e.g. `doc/demo-1.sh`).
This will source the file `demo-0.in` which will explain the panes,
start okapi, and send some basic requests. The story script will continue
to send its requests (in this case also running the `doc/okapi-examples.sh`).

Switch to the tmux session.

If you are recording, then issue the `Ctrl-b d` command when ready to
detach and stop the session.
There will be an output file for your story.

Then do: `asciinema play doc/demo-1.json`

To configure the shell prompt for each pane, add the following to
`~/.bash_profile` before starting:

```
# For Okapi asciinema:
case $TERM in
  screen*)
    PS1="pane#$(( ${TMUX_PANE#%} + 1 )):\W\$ "
  ;;
esac
```

Improvements are welcome.
