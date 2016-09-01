#!/bin/bash

SESSION_NAME="${1:-okapi}"
demo_name="Demo #1"

window="${SESSION_NAME}:1"
pane1="${window}.1"
pane2="${window}.2"
pane3="${window}.3"
pane4="${window}.4"

function has_session {
  tmux has-session -t "${SESSION_NAME}" 2>/dev/null
}

if ! has_session ; then
  echo "The tmux session '${SESSION_NAME}' does not exist."
  exit 1
fi

if ! [ -f 'doc/guide.md' ]; then
  echo "Run this script from the top-level 'okapi' directory."
  echo "See 'doc/demos.md' document."
  exit 1
fi

# Set the filename for the top-level recording script to rename its output.
tmux set-environment -t "${SESSION_NAME}" output_filename "${0%.*}.json"

tmux send-keys -t "${pane1}" "# Starting the FOLIO Okapi ${demo_name}" C-m
sleep 2
tmux send-keys -t "${pane1}" "# We are following the 'Okapi Guide and Reference' [1]" C-m
tmux send-keys -t "${pane4}" "# [1] https://github.com/folio-org/okapi/blob/master/doc/guide.md#running-okapi-itself" C-m
sleep 2

source 'doc/demo-0.in'

sleep 1
tmux send-keys -t "${pane3}" "# Now running the script 'doc/okapi-examples.sh' which extracts the example 'curl' commands from the guide.md and runs them all." C-m
sleep 2
tmux send-keys -t "${pane3}" "doc/okapi-examples.sh 'http://localhost:9130' 'doc/guide.md' 5" C-m
