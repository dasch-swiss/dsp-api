module.exports = {
    "dataSource": "issues",
    "prefix": "",
    "groupBy": {
        "Breaking Changes": ["breaking"],
        "Enhancements:": ["enhancement"],
        "Bug Fixes:": ["bug"],
        "Documentation:": ["documentation"],
        "Other": ["chore", "refactor"]
    },
    "template": {
        "issue": "- [{{text}}]({{url}}) {{name}}"
    }
};
