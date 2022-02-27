import com.github.alexlandau.ghss.Gh

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

    override fun createPr(title: String, body: String, baseBranch: String, headBranch: String) {
        val pr = MockPr(title, body, baseBranch, headBranch)
        val prNumber = prs.size + 1
        prs.put(prNumber, pr)
    }

    override fun editPr(prNumber: Int, title: String, body: String, baseBranch: String) {
        val pr = prs.getValue(prNumber)
        pr.title = title
        pr.body = body
        pr.baseBranch = baseBranch
    }
}