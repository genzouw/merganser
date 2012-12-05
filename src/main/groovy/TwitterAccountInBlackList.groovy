import javax.persistence.*

@Entity class TwitterAccountInBlackList {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long id

    public Long accountId

    public String nickName = ""

    public String screenName = ""

    @ManyToOne(targetEntity = TwitterAccount.class) 
    @JoinColumn(name = "twitterAccountId") 
    public TwitterAccount twitterAccount = null

    public Date lastUpdated = null

}

