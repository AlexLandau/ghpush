package com.github.alexlandau.ghpush

class MockGh: Gh {
    private val prs: MutableMap<Int, MockPr> = HashMap()

    private data class MockPr(
        var title: String,
        var body: String,
        var baseBranch: String,
        val headBranch: String
    )

    override fun findPrNumber(ghBranchName: String): Int? {
        return prs.entries.find { it.value.headBranch == ghBranchName }?.key
    }

    override fun createPr(title: String, body: String, baseBranch: String, headBranch: String): CreatePrResult {
        val pr = MockPr(title, body, baseBranch, headBranch)
        val prNumber = prs.size + 1
        prs.put(prNumber, pr)
        return CreatePrResult(prNumber, fakeUrl(prNumber))
    }

    override fun editPr(prNumber: Int, title: String, body: String, baseBranch: String): EditPrResult {
        val pr = prs.getValue(prNumber)
        pr.title = title
        pr.body = body
        pr.baseBranch = baseBranch
        return EditPrResult(fakeUrl(prNumber))
    }

    override fun editPrBody(prNumber: Int, body: String): EditPrResult {
        val pr = prs.getValue(prNumber)
        pr.body = body
        return EditPrResult(fakeUrl(prNumber))
    }

    override fun getUserLogin(): String {
        return "TestUser"
    }

    override fun getDefaultBranchRef(): String {
        return "main"
    }

    fun fakeUrl(prNumber: Int): String {
        return "https://github.example.com/OriginMaintainer/test-repo/pull/$prNumber"
    }
}