
    create table glossary_entry (
        id uuid not null,
        english_term varchar(200) not null,
        original_korean_term varchar(200) not null,
        processed_korean_term varchar(200) not null,
        primary key (id)
    );

    create table sofia_user (
        id uuid not null,
        student_name varchar(255),
        student_number varchar(255) unique,
        primary key (id)
    );

    create table sofia_user_auth (
        id uuid not null,
        plusfriend_user_key varchar(255),
        role varchar(255) check ((role in ('STUDENT','ADMIN'))),
        secret_token uuid,
        primary key (id)
    );

    create table sofia_user_email (
        id uuid not null,
        email varchar(255),
        is_unsubscribed boolean not null,
        unsubscribe_token uuid,
        primary key (id)
    );

    create table sofia_user_task_status (
        id uuid not null,
        adjusted_char_count integer not null,
        last_assigned_at timestamp(6) with time zone,
        rest boolean not null,
        warning_count integer not null,
        primary key (id)
    );

    create table system_phase_entity (
        id integer not null,
        current_phase varchar(255) check ((current_phase in ('DEACTIVATION','RECRUITMENT','TRANSLATION','SETTLEMENT'))),
        primary key (id)
    );

    create table translation_task (
        id uuid not null,
        assigned_at timestamp(6) with time zone,
        assignment_type varchar(255) check ((assignment_type in ('AUTOMATIC','MANUAL'))),
        character_count integer,
        completed_at timestamp(6) with time zone,
        reminded_at timestamp(6) with time zone,
        task_description varchar(50) unique,
        task_type varchar(255) check ((task_type in ('GAONNURI_POST','EXTERNAL_POST'))),
        assignee_id uuid,
        primary key (id)
    );

    create table user_registration (
        id uuid not null,
        plusfriend_user_key varchar(255),
        student_name varchar(255),
        student_number varchar(255) unique,
        primary key (id)
    );

    alter table if exists sofia_user_auth 
       add constraint FKhskgq1pmcvoyohy6mgis6pt86 
       foreign key (id) 
       references sofia_user;

    alter table if exists sofia_user_email 
       add constraint FKje5mx3ak18xnhevs5iysjxhri 
       foreign key (id) 
       references sofia_user;

    alter table if exists sofia_user_task_status 
       add constraint FKcd2atkmk0sy93rh5jbyjc9w53 
       foreign key (id) 
       references sofia_user;

    alter table if exists translation_task 
       add constraint FK35okg8cacogc2yby5wpwpnxsa 
       foreign key (assignee_id) 
       references sofia_user;
