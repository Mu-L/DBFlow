package com.dbflow5

import com.dbflow5.annotation.Column
import com.dbflow5.annotation.PrimaryKey
import com.dbflow5.annotation.Table
import com.dbflow5.contentobserver.ContentObserverDatabase

@Table(database = ContentObserverDatabase::class, name = "User2")
class User(@PrimaryKey var id: Int = 0,
           @Column var firstName: String? = null,
           @Column var lastName: String? = null,
           @Column var email: String? = null)
