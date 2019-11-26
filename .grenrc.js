module.exports = {
    "dataSource": "milestones",
    "prefix": "",
    "ignoreIssuesWith": [
        "duplicate",
        "wontfix",
        "invalid",
        "help wanted",
        "question",
        "testing",
        "test"
    ],
    "groupBy": {
        "Breaking Changes": ["breaking/data", "breaking/api"],
        "Enhancements:": ["enhancement"],
        "Bug Fixes:": ["bug"],
        "Documentation:": ["documentation"],
        "Other": ["chore", "refactor"]
    },
    "template": {
        "issue": "- [{{text}}]({{url}}) {{name}}"
    }
};
