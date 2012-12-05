import javax.persistence.*

@Entity class TwitterAccountInFollowingList {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long id

    public Long accountId

    @ManyToOne(targetEntity = TwitterAccount.class) 
    @JoinColumn(name = "twitterAccountId") 
    public TwitterAccount twitterAccount = null

    public Date lastUpdated = null

}

