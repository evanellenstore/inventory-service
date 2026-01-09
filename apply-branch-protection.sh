#!/bin/bash

OWNER="evanellenstore"
REPO="inventory-service"
APPROVALS=1
CI_CONTEXTS=()  # Example: ("build" "test")
DRY_RUN=false
set -e

protect_branch() {
  BRANCH=$1
  echo "--------------------------------------------"
  echo "Checking branch protection for: $BRANCH"

  # Check if branch has protection
  if gh api repos/$OWNER/$REPO/branches/$BRANCH/protection &>/dev/null; then
    echo "Protection exists. Updating..."
  else
    echo "Branch not protected. Applying new protection..."
  fi

  # Build JSON payload in temp file
  PAYLOAD=$(mktemp)
  jq -n \
    --argjson approvals "$APPROVALS" \
    --argjson contexts "$(jq -n '$ARGS.positional' --args "${CI_CONTEXTS[@]}")" \
    '{
      required_pull_request_reviews: {
        required_approving_review_count: $approvals,
        dismiss_stale_reviews: true,
        require_code_owner_reviews: false
      },
      required_status_checks: {
        strict: true,
        contexts: $contexts
      },
      enforce_admins: true,
      restrictions: null,
      allow_force_pushes: false,
      allow_deletions: false
    }' > "$PAYLOAD"

  if [ "$DRY_RUN" = true ]; then
    echo "Dry-run payload for $BRANCH:"
    cat "$PAYLOAD" | jq
  else
    # Use --input to pass JSON via file
    gh api -X PUT repos/$OWNER/$REPO/branches/$BRANCH/protection \
      -H "Accept: application/vnd.github+json" \
      --input "$PAYLOAD"

    echo "✅ Protection applied for branch: $BRANCH"
  fi

  rm -f "$PAYLOAD"
}

# Protect main & development
protect_branch "main"
protect_branch "development"

# Protect release/* branches
RELEASE_BRANCHES=$(gh api repos/$OWNER/$REPO/branches --jq '.[] | select(.name | startswith("release/")) | .name')
for BRANCH in $RELEASE_BRANCHES; do
  protect_branch "$BRANCH"
done

echo "============================================"
echo "Branch protection script finished."
