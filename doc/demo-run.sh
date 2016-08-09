#!/bin/bash

function usage {
  cat <<END
usage: $0 options

This script will set up a 'tmux' terminal session with a number of panes
for running a local test okapi instance and sending a suite of requests.

If the 'asciinema' program is available, then the session can be recorded
for later playback.

In a separate window, run a story script, e.g. doc/demo-1.sh

If using asciinema, then issue the 'Ctrl-b d' command to detach and stop
the session. There will be an output file for your story. Then do, e.g.:
 asciinema play doc/demo-1.json

Options:
  -h    Show these usage instructions.
  -s    The tmux session name (default: okapi).
  -a    Record with asciinema (default: false).
END
}

function has_session {
  tmux has-session -t "${SESSION_NAME}" 2>/dev/null
}

SESSION_NAME="okapi"
RECORD=false

if ! [ -x "$(command -v tmux)" ]; then
  echo "Missing dependency: tmux, so stopping." >&2
  exit 1
fi

if ! [ -f 'doc/guide.md' ]; then
  echo "Run this script from the top-level 'okapi' directory."
  echo "See 'doc/demos.md' document."
  exit 1
fi

if ! [ -x "$(command -v asciinema)" ]; then
  echo "Missing dependency: asciinema, so not able to record." >&2
fi

while getopts "hs:a" OPTION
do
  case "${OPTION}" in
    h) usage; exit 3;;
    s)
      SESSION_NAME=$OPTARG
      ;;
    a)
      RECORD=true
      ;;
  esac
done

if has_session ; then
  echo "The tmux session '${SESSION_NAME}' already exists."
  echo "Probably need to do: tmux kill-session -t ${SESSION_NAME}"
else
  tmux new-session -d -s "${SESSION_NAME}"
  tmux set -t "${SESSION_NAME}" base-index 1
  tmux set -t "${SESSION_NAME}" pane-base-index 1
  tmux set -t "${SESSION_NAME}" display-time 4000
  tmux set -t "${SESSION_NAME}" display-panes-time 3000
  tmux set -t "${SESSION_NAME}" status off
  tmux split-window -v -p 90
  tmux split-window -v -p 80
  tmux split-window -v -p 15
  tmux select-pane -t "${SESSION_NAME}:1.1"
  if [ "${RECORD}" = true ]; then
    asciinema rec -c "tmux attach -t ${SESSION_NAME}" asciinema-demo.json
    output_filename="$(tmux show-environment -t ${SESSION_NAME} output_filename | sed 's/^.*=//')"
    if [ -e 'asciinema-demo.json' ]; then
      mv 'asciinema-demo.json' "${output_filename}"
    fi
    tmux kill-session -t "${SESSION_NAME}"
  else
    tmux attach -t "${SESSION_NAME}"
  fi
fi
