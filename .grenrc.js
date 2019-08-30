module.exports = {
    "dataSource": "issues",
    "prefix": "",
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
