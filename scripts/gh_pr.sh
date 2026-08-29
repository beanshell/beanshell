#!/bin/bash

# BeanShell GitHub PR Manager (Enhanced)
# Requires: curl, git, jq

REPO="beanshell/beanshell"
API_URL="https://api.github.com/repos/$REPO"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

function list_prs() {
    echo -e "${BOLD}Fetching open pull requests for $REPO...${NC}"

    # Fetch PRs and their statuses in parallel-ish or sequential for simplicity
    local prs=$(curl -s "$API_URL/pulls")

    echo "$prs" | jq -r '.[] | "\(.number)\t\(.user.login)\t\(.title)"' | while read -r line; do
        id=$(echo "$line" | cut -f1)
        user=$(echo "$line" | cut -f2)
        title=$(echo "$line" | cut -f3)

        # Get CI status (aggregated from check-runs)
        local status_json=$(curl -s "$API_URL/commits/pull/$id/head/check-runs")
        local conclusions=$(echo "$status_json" | jq -r '.check_runs[].conclusion')
        local conclusion="success"
        if echo "$conclusions" | grep -q "failure"; then
            conclusion="failure"
        elif [ -z "$conclusions" ] || echo "$conclusions" | grep -qE "pending|null"; then
            conclusion="pending"
        fi

        case "$conclusion" in
            success) status_icon="${GREEN}●${NC}" ;;
            failure) status_icon="${RED}●${NC}" ;;
            *) status_icon="${YELLOW}○${NC}" ;;
        esac

        printf " #%-4s %b  %-15s %s\n" "$id" "$status_icon" "@$user" "$title"
    done
}

function show_pr() {
    local pr_id=$1
    if [ -z "$pr_id" ]; then
        echo "Usage: $0 show <pr_number>"
        exit 1
    fi

    local pr_data=$(curl -s "$API_URL/pulls/$pr_id")
    local title=$(echo "$pr_data" | jq -r '.title')
    local user=$(echo "$pr_data" | jq -r '.user.login')
    local body=$(echo "$pr_data" | jq -r '.body')
    local url=$(echo "$pr_data" | jq -r '.html_url')
    local labels=$(echo "$pr_data" | jq -r '.labels[].name' | paste -sd "," -)

    echo -e "${BOLD}PR #$pr_id: $title${NC}"
    echo -e "${BLUE}Author:${NC} @$user"
    echo -e "${BLUE}URL:${NC}    $url"
    echo -e "${BLUE}Labels:${NC} $labels"
    echo -e "----------------------------------------------------"
    echo -e "$body"
    echo -e "----------------------------------------------------"

    echo -e "${BOLD}Files Changed:${NC}"
    curl -s "$API_URL/pulls/$pr_id/files" | jq -r '.[] | "  \(if .status == "modified" then "M" else "A" end) \(.filename)"'
}

function diff_pr() {
    local pr_id=$1
    if [ -z "$pr_id" ]; then
        echo "Usage: $0 diff <pr_number>"
        exit 1
    fi

    echo -e "${BOLD}Fetching diff for PR #$pr_id...${NC}"
    git fetch origin master > /dev/null 2>&1 || { echo -e "${RED}Error: Failed to fetch master.${NC}"; exit 1; }
    git fetch origin pull/$pr_id/head > /dev/null 2>&1 || { echo -e "${RED}Error: Failed to fetch PR head.${NC}"; exit 1; }
    git diff origin/master...FETCH_HEAD
}

function checkout_pr() {
    local pr_id=$1
    if [ -z "$pr_id" ]; then
        echo "Usage: $0 checkout <pr_number>"
        exit 1
    fi
    echo -e "${BOLD}Checking out PR #$pr_id...${NC}"
    git fetch origin pull/$pr_id/head:pr-$pr_id || { echo -e "${RED}Error: Failed to fetch PR.${NC}"; exit 1; }
    git checkout pr-$pr_id || { echo -e "${RED}Error: Failed to checkout PR branch.${NC}"; exit 1; }
}

function test_pr() {
    local pr_id=$1
    if [ -z "$pr_id" ]; then
        echo "Usage: $0 test <pr_number>"
        exit 1
    fi

    local original_state=$(git rev-parse HEAD)
    local original_branch=$(git rev-parse --abbrev-ref HEAD)

    echo -e "${BOLD}Testing PR #$pr_id...${NC}"
    git fetch origin pull/$pr_id/head > /dev/null 2>&1 || { echo -e "${RED}Error: Failed to fetch PR.${NC}"; exit 1; }
    git checkout FETCH_HEAD --detach > /dev/null 2>&1 || { echo -e "${RED}Error: Failed to checkout PR.${NC}"; exit 1; }

    echo -e "${YELLOW}Running Maven tests...${NC}"
    mvn test
    local result=$?

    if [ "$original_branch" = "HEAD" ]; then
        git checkout "$original_state" --detach > /dev/null 2>&1
    else
        git checkout "$original_branch" > /dev/null 2>&1
    fi

    if [ $result -eq 0 ]; then
        echo -e "${GREEN}Tests PASSED for PR #$pr_id${NC}"
    else
        echo -e "${RED}Tests FAILED for PR #$pr_id${NC}"
    fi
    return $result
}

function merge_pr() {
    local pr_id=$1
    if [ -z "$pr_id" ]; then
        echo "Usage: $0 merge <pr_number>"
        exit 1
    fi

    local current_branch=$(git rev-parse --abbrev-ref HEAD)
    echo -e "${BOLD}Merging PR #$pr_id into $current_branch...${NC}"

    if ! git diff-index --quiet HEAD --; then
        echo -e "${RED}Error: You have unstaged changes. Please commit or stash them first.${NC}"
        exit 1
    fi

    git fetch origin pull/$pr_id/head || { echo -e "${RED}Error: Failed to fetch PR.${NC}"; exit 1; }
    git merge FETCH_HEAD -m "Merge pull request #$pr_id from GitHub"
}

case "$1" in
    list) list_prs ;;
    show) show_pr "$2" ;;
    diff) diff_pr "$2" ;;
    checkout) checkout_pr "$2" ;;
    test) test_pr "$2" ;;
    merge) merge_pr "$2" ;;
    *)
        echo -e "${BOLD}BeanShell PR Manager${NC}"
        echo "Usage: $0 {list|show|diff|checkout|test|merge} [pr_number]"
        exit 1
        ;;
esac
