ger.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val communityPlaylists by viewModel.communityPlaylists.collectAsStateWithLifecycle()

    val allLocalItems by viewModel.allLocalItems.collectAsStateWithLifecycle()
    val allYtItems by viewModel.allYtItems.collectAsStateWithLifecycle()
    val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val pinnedSpeedDialItems by viewModel.pinnedSpeedDialItems.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()

    // Official podcast API data
    val savedPodcastShows by viewModel.savedPodcastShows.collectAsStateWithLifecycle()
    val episodesForLater by viewModel.episodesForLater.collectAsStateWithLifecycle()

    val isLoading: Boolean by viewModel.isLoading.collectAsStateWithLifecycle()
    val isMoodAndGenresLoading = isLoading && explorePage?.moodAndGenres == null
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRandomizing by viewModel.isRandomizing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, true)
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val shouldShowWrappedCard by viewModel.showWrappedCard.collectAsStateWithLifecycle()
    val wrappedState by viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    val isWrappedDataReady = wrappedState.isDataReady

    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }
    val url = if (isLoggedIn) accountImageUrl else null

    // Extract unique podcasts from episodes for "Podcast Channels" row
    // Cache the podcasts to prevent them from disappearing during refresh
    var cachedPodcasts by remember { mutableStateOf<List<PodcastItem>>(emptyList()) }

    val featuredPodcasts =
        remember(homePage, selectedChip) {
            if (selectedChip == null) {
                cachedPodcasts = emptyList()
                emptyList()
            } else {
                val newPodcasts =
                    homePage
                        ?.sections
                        ?.flatMap { it.items }
                        ?.filterIsInstance<EpisodeItem>()
                        ?.mapNotNull { episode ->
                            episode.podcast?.let { podcast ->
                                PodcastItem(
                                    id = podcast.id,
                                    title = podcast.name,
                                    author = episode.author,
                                    episodeCountText = null,
                                    thumbnail = episode.thumbnail,
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                )
                            }
                        }?.distinctBy { it.id }
                        ?.shuffled()
                        ?.take(10)
                        ?: emptyList()

                // Only update cache if we got valid data; keep old data during refresh
                if (newPodcasts.isNotEmpty()) {
                    cachedPodcasts = newPodcasts
                }
                cachedPodcasts
            }
        }

    val scope = rememberCoroutineScope()
    // Track randomization job
    var randomizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val lazylistState = rememberLazyListState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val currentGridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val wrappedDismissed by backStackEntry
        ?.savedStateHandle
        ?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }

    val foundInSettings = stringResource(R.string.found_in_settings_content)
    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            scope.launch {
                snackbarHostState.showSnackbar(foundInSettings)
            }
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false) // Reset the value
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            lazylistState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            val len = lazylistState.layoutInfo.totalItemsCount
            if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                viewModel.loadMoreYouTubeItems(homePage?.continuation)
            }
        }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> {
                SongGridItem(
                    song = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (!isListenTogetherGuest) {
                                        if (it.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                if (autoRadioQueue) {
                                                    YouTubeQueue.radio(it.toMediaMetadata())
                                                } else {
                                                    ListQueue(
                                                        title = it.title,
                                                        items = listOf(it.toMediaItem())
                                                    )
                                                }
                                            )
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress,
                                    )
                                    menuState.show {
                                        SongMenu(
                                            originalSong = it,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    isActive = it.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                )
            }

            is Album -> {
                AlbumGridItem(
                    album = it,
                    isActive = it.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    coroutineScope = scope,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("album/${it.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        AlbumMenu(
                                            originalAlbum = it,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                )
            }

            is Artist -> {
                ArtistGridItem(
                    artist = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("artist/${it.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress,
                                    )
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = it,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                )
            }

            is Playlist -> {}
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (!isListenTogetherGuest) {
                                        playerConnection.playQueue(
                                            if (autoRadioQueue) {
                                                YouTubeQueue(
                                                    item.endpoint ?: WatchEndpoint(
                                                        videoId = item.id,
                                                    ),
                                                    item.toMediaMetadata(),
                                                )
                                            } else {
                                                ListQueue(
                                                    title = item.title,
                                                    items = listOf(item.toMediaItem())
                                                )
                                            }
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                }

                                is PodcastItem -> {
                                    navController.navigate("online_podcast/${item.id}")
                                }

                                is EpisodeItem -> {
                                    if (!isListenTogetherGuest) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = item.title,
                                                items = listOf(item.toMediaMetadata().toMediaItem()),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                when (item) {
                                    is SongItem -> {
                                        YouTubeSongMenu(
                                            song = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is AlbumItem -> {
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is ArtistItem -> {
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is PlaylistItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is PodcastItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item.asPlaylistItem(),
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is EpisodeItem -> {
                                        YouTubeSongMenu(
                                            song = item.asSongItem(),
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            }
                        },
                    ),
        )
    }

    val homeSections =
        remember(
            randomizeHomeOrder,
            randomSeed,
            selectedChip,
            speedDialItems,
            quickPicks,
            dailyDiscover,
            keepListening,
            accountPlaylists,
            forgottenFavorites,
            communityPlaylists,
            similarRecommendations,
            homePage?.sections,
            explorePage?.moodAndGenres,
        ) {
            val list = mutableListOf<HomeSection>()
            val chipActive = selectedChip != null

            if (!chipActive && speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (!chipActive && quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
            if (!chipActive && communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
            if (!chipActive && dailyDiscover?.isNotEmpty() == true) list.add(HomeSection.DailyDiscover)
            if (!chipActive && keepListening?.isNotEmpty() == true) list.add(HomeSection.KeepListening)
            if (!chipActive && accountPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
            if (!chipActive && forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.ForgottenFavorites)

            if (!chipActive) {
                similarRecommendations?.indices?.forEach { i ->
                    list.add(HomeSection.SimilarRecommendation(i))
                }
            }

            homePage?.sections?.indices?.forEach { i ->
                list.add(HomeSection.HomePageSection(i))
            }

            if (explorePage?.moodAndGenres != null) list.add(HomeSection.MoodAndGenres)

            if (randomizeHomeOrder) {
                list.sortedByDescending { section ->
                    // Use a stable seed for each section based on the session seed + section ID hash
                    // This ensures the weight for a specific section remains constant during a session (until refresh)
                    // even if other sections appear/disappear, preventing jumping.
                    val sectionRandom = Random(randomSeed + section.id.hashCode())

                    // Flatten the base values to allow for more overlap and variation
                    // All "main" sections start closer together
                    val base =
                        when (section) {
                            HomeSection.SpeedDial,
                            HomeSection.QuickPicks,
                            HomeSection.DailyDiscover,
                            -> 500

                            // Top tier starts equal

                            HomeSection.KeepListening,
                            HomeSection.AccountPlaylists,
                            HomeSection.ForgottenFavorites,
                            HomeSection.FromTheCommunity,
                            -> 300

                            // Middle tier starts equal

                            else -> 100 // Bottom tier
                        }

                    val modifier =
                        when (section) {
                            // Top tier: High variance to allow shuffling among themselves
                            // Range: [500-200, 500+400] = [300, 900]
                            HomeSection.SpeedDial,
                            HomeSection.QuickPicks,
                            HomeSection.DailyDiscover,
                            -> sectionRandom.nextInt(-200, 400)

                            // Middle tier: Can jump up to challenge top tier, or drop lower
                            // Range: [300-100, 300+400] = [200, 700]
                            // This allows them to occasionally appear above a "bad roll" top tier item
                            HomeSection.KeepListening,
                            HomeSection.AccountPlaylists,
                            HomeSection.ForgottenFavorites,
                            HomeSection.FromTheCommunity,
                            -> sectionRandom.nextInt(-100, 400)

                            // Bottom tier: Standard variance
                            else -> sectionRandom.nextInt(-50, 50)
                        }
                    base + modifier
                }
            } else {
                val defaultOrder =
                    mapOf(
                        HomeSection.SpeedDial to 100,
                        HomeSection.QuickPicks to 90,
                        HomeSection.FromTheCommunity to 80,
                        HomeSection.DailyDiscover to 70,
                        HomeSection.KeepListening to 60,
                        HomeSection.AccountPlaylists to 50,
                        HomeSection.ForgottenFavorites to 40,
                        HomeSection.MoodAndGenres to 10,
                    )

                list.sortedByDescending { section ->
                    when (section) {
                        is HomeSection.SimilarRecommendation -> 30 - section.index
                        is HomeSection.HomePageSection -> 20 - section.index
                        else -> defaultOrder[section] ?: 0
                    }
                }
            }
        }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider =
                remember(quickPicksLazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = quickPicksLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        },
                    )
                }
            val forgottenFavoritesSnapLayoutInfoProvider =
                remember(forgottenFavoritesLazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = forgottenFavoritesLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        },
                    )
                }

            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item {
                    ChipsRow(
                        chips = homePage?.chips?.map { it to it.title } ?: emptyList(),
                        currentValue = selectedChip,
                        onValueUpdate = {
                            viewModel.toggleChip(it)
                        },
                    )
                }

                if (isLoading && homePage?.chips.isNullOrEmpty()) {
                    item(key = "chips_shimmer") {
                        ShimmerHost(showGradient = false) {
                            LazyRow(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                items(5) {
                                    TextPlaceholder(
                                        height = 30.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.width(72.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Show podcast sections FIRST when podcast chip is selected (fixed at top)
                if (selectedChip?.title?.contains("Podcast", ignoreCase = true) == true) {
                    // Show "Your Shows" section from official API
                    if (savedPodcastShows.isNotEmpty()) {
                        item(key = "00_your_shows_title") {
                            NavigationTitle(
                                title = stringResource(R.string.your_shows),
                                onClick = {
                                    navController.navigate("youtube_browse/FEmusic_library_non_music_audio_list")
                                },
                            )
                        }

                        item(key = "00_your_shows_list") {
                            LazyRow(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                            ) {
                                items(savedPodcastShows.distinctBy { it.id }, key = { "home_saved_podcast_${it.id}" }) { podcast ->
                                    ytGridItem(podcast)
                                }
                            }
                        }
                    }

                    // Show "Episodes for Later" section from official API
                    if (episodesForLater.isNotEmpty()) {
                        item(key = "00_episodes_for_later_title") {
                            NavigationTitle(
                                title = stringResource(R.string.episodes_for_later),
                                onClick = {
                                    navController.navigate("online_playlist/SE")
                                },
                            )
                        }

                        item(key = "00_episodes_for_later_list") {
                            LazyRow(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                            ) {
                                items(episodesForLater.distinctBy { it.id }, key = { "home_episode_later_${it.id}" }) { episode ->
                                    ytGridItem(episode)
                                }
                            }
                        }
                    }

                    // Show Podcast Channels row if we have any (extracted from episodes)
                    // Only show if "Your Shows" from official API is empty (to avoid duplicates)
                    if (featuredPodcasts.isNotEmpty() && savedPodcastShows.isEmpty()) {
                        item(key = "0_podcast_channels_title") {
                            NavigationTitle(
                                title = stringResource(R.string.podcast_channels),
                            )
                        }

                        item(key = "0_podcast_channels_list") {
                            LazyRow(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                            ) {
                                items(featuredPodcasts.distinctBy { it.id }, key = { "home_featured_podcast_${it.id}" }) { podcast ->
                                    ytGridItem(podcast)
                                }
                            }
                        }
                    }

                    // Render the regular sections from the chip (episodes grouped by category)
                    // Use key prefix "1_" to ensure episodes sort after channels "0_"
                    // Skip sections that duplicate official API sections (Your Shows, Episodes for Later)
                    homeSections.filterIsInstance<HomeSection.HomePageSection>().forEach { section ->
                        val sectionData = homePage?.sections?.getOrNull(section.index)
                        // Skip if this section duplicates an official API section
                        val skipTitles = listOf("your shows", "episodes for later", "podcast channels", "new episodes")
                        if (sectionData?.title?.lowercase()?.let { title -> skipTitles.any { title.contains(it) } } == true) {
                            return@forEach
                        }
                        sectionData?.let {
                            item(key = "1_chip_section_title_${section.index}") {
                                NavigationTitle(
                                    title = sectionData.title,
                                    label = sectionData.label,
                                    thumbnail =
                                        sectionData.thumbnail?.let { thumbnailUrl ->
                                            {
                                                val shape =
                                                    if (sectionData.endpoint?.isArtistEndpoint == true) {
                                                        CircleShape
                                                    } else {
                                                        RoundedCornerShape(
                                                            ThumbnailCornerRadius,
                                                        )
                                                    }
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier =
                                                        Modifier
                                                            .size(ListThumbnailSize)
                                                            .clip(shape),
                                                )
                                            }
                                        },
                                    onClick =
                                        sectionData.endpoint?.let { endpoint ->
                                            {
                                                when {
                                                    endpoint.browseId == "FEmusic_moods_and_genres" -> {
                                                        navController.navigate("mood_and_genres")
                                                    }

                                                    endpoint.params != null -> {
                                                        navController.navigate(
                                                            "youtube_browse/${endpoint.browseId}?params=${endpoint.params}",
                                                        )
                                                    }

                                                    else -> {
                                                        navController.navigate("browse/${endpoint.browseId}")
                                                    }
                                                }
                                            }
                                        },
                                )
                            }

                            item(key = "1_chip_section_list_${section.index}") {
                                LazyRow(
                                    contentPadding =
                                        WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                ) {
                                    items(sectionData.items.distinctBy { it.id }, key = { "home_chip_section_${it.id}" }) { item ->
                                        ytGridItem(item)
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedChip == null) {
                    item(key = "wrapped_card") {
                        AnimatedVisibility(visible = shouldShowWrappedCard) {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isWrappedDataReady) {
                                        val bbhFont =
                                            try {
                                                FontFamily(Font(R.font.bbh_bartle_regular))
                                            } catch (e: Exception) {
                                                FontFamily.Default
                                            }
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.wrapped_ready_title),
                                                style =
                                                    MaterialTheme.typography.headlineLarge.copy(
                                                        fontFamily = bbhFont,
                                                        textAlign = TextAlign.Center,
                                                    ),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.wrapped_ready_subtitle),
                                                style =
                                                    MaterialTheme.typography.bodyLarge.copy(
                                                        textAlign = TextAlign.Center,
                                                    ),
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = {
                                                navController.navigate("wrapped")
                                            }) {
                                                Text(stringResource(R.string.open))
                                            }
                                        }
                                    } else {
                                        ContainedLoadingIndicator()
                                    }
                                }
                            }
                        }
                    }
                }

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "speed_dial_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.speed_dial),
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    val targetItemSize = 160.dp
                                    val availableWidth = maxWidth - 32.dp
                                    val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
                                    val rows =
                                        if (columns >= 6) {
                                            1
                                        } else if (columns >= 4) {
                                            2
                                        } else {
                                            3
                                        }
                                    val itemsPerPage = columns * rows
                                    val itemWidth = availableWidth / columns

                                    val pagerState = rememberPagerState(pageCount = { (items.size + itemsPerPage - 1) / itemsPerPage })

                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(),
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            pageSpacing = 16.dp,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(itemWidth * rows),
                                        ) { page ->
                                            val pageStartIndex = page * itemsPerPage
                                            val pageItems = items.drop(pageStartIndex).take(itemsPerPage)

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                for (row in 0 until rows) {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        for (col in 0 until columns) {
                                                            val itemIndex = row * columns + col

                                                            val isRandomizeSlot = (page == 0 && itemIndex == itemsPerPage - 1)

                                                            if (isRandomizeSlot) {
                                                                Box(
                                                                    modifier =
                                                                        Modifier
                                                                            .width(itemWidth)
                                                                            .height(itemWidth)
                                                                            .padding(4.dp),
                                                                ) {
                                                                    RandomizeGridItem(
                                                                        isLoading = isRandomizing,
                                                                        onClick = {
                                                                            if (isRandomizing) {
                                                                                randomizeJob?.cancel()
                                                                            } else if (!isListenTogetherGuest) {
                                                                                randomizeJob =
                                                                                    scope.launch {
                                                                                        val randomItem = viewModel.getRandomItem()
                                                                                        if (randomItem != null) {
                                                                                            when (randomItem) {
                                                                                                is SongItem -> {
                                                                                                    playerConnection.playQueue(
                                                                                                        if (autoRadioQueue) {
                                                                                                            YouTubeQueue(
                                                                                                                randomItem.endpoint
                                                                                                                    ?: WatchEndpoint(
                                                                                                                        videoId = randomItem.id,
                                                                                                                    ),
                                                                                                                randomItem.toMediaMetadata(),
                                                                                                            )
                                                                                                        } else {
                                                                                                            ListQueue(
                                                                                                                title = randomItem.title,
                                                                                                                items = listOf(randomItem.toMediaItem())
                                                                                                            )
                                                                                                        }
                                                                                                    )
                                                                                                }

                                                                                                is AlbumItem -> {
                                                                                                    navController.navigate(
                                                                                                        "album/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is ArtistItem -> {
                                                                                                    navController.navigate(
                                                                                                        "artist/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is PlaylistItem -> {
                                                                                                    navController.navigate(
                                                                                                        "online_playlist/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is PodcastItem -> {
                                                                                                    navController.navigate(
                                                                                                        "online_podcast/${randomItem.id}",
                                                                                                    )
                                                                                                }

                                                                                                is EpisodeItem -> {
                                                                                                    playerConnection.playQueue(
                                                                                                        ListQueue(
                                                                                                            title = randomItem.title,
                                                                                                            items =
                                                                                                                listOf(
                                                                                                                    randomItem
                                                                                                                        .toMediaMetadata()
                                                                                                                        .toMediaItem(),
                                                                                                                ),
                                                                                                        ),
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                            }
                                                                        },
                                                                    )
                                                                }
                                                            } else if (itemIndex < pageItems.size) {
                                                                val item = pageItems[itemIndex]
                                                                val isPinned by database.speedDialDao
                                                                    .isPinned(
                                                                        item.id,
                                                                    ).collectAsStateWithLifecycle(initialValue = false)

                                                                Box(
                                                                    modifier =
                                                                        Modifier
                                                                            .width(itemWidth)
                                                                            .height(itemWidth)
                                                                            .padding(4.dp),
                                                                ) {
                                                                    SpeedDialGridItem(
                                                                        item = item,
                                                                        isPinned = isPinned,
                                                                        isActive =
                                                                            item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                                                                        isPlaying = isPlaying,
                                                                        modifier =
                                                                            Modifier
                                                                                .fillMaxSize()
                                                                                .combinedClickable(
                                                                                    onClick = {
                                                                                        when (item) {
                                                                                            is SongItem -> {
                                                                                                if (!isListenTogetherGuest) {
                                                                                                    playerConnection.playQueue(
                                                                                                        if (autoRadioQueue) {
                                                                                                            YouTubeQueue(
                                                                                                                item.endpoint
                                                                                                                    ?: WatchEndpoint(
                                                                                                                        videoId = item.id,
                                                                                                                    ),
                                                                                                                item.toMediaMetadata(),
                                                                                                            )
                                                                                                        } else {
                                                                                                            ListQueue(
                                                                                                                title = item.title,
                                                                                                                items = listOf(item.toMediaItem())
                                                                                                            )
                                                                                                        }
                                                                                                    )
                                                                                                }
                                                                                            }

                                                                                            is AlbumItem -> {
                                                                                                navController.navigate("album/${item.id}")
                                                                                            }

                                                                                            is ArtistItem -> {
                                                                                                navController.navigate("artist/${item.id}")
                                                                                            }

                                                                                            is PlaylistItem -> {
                                                                                                val rawType =
                                                                                                    pinnedSpeedDialItems
                                                                                                        .find {
                                                                                                            it.id ==
                                                                                                                item.id
                                                                                                        }?.type
                                                                                                if (rawType == "LOCAL_PLAYLIST") {
                                                                                                    navController.navigate(
                                                                                                        "local_playlist/${item.id}",
                                                                                                    )
                                                                                                } else {
                                                                                                    navController.navigate(
                                                                                                        "online_playlist/${item.id}",
                                                                                                    )
                                                                                                }
                                                                                            }

                                                                                            is PodcastItem -> {
                                                                                                navController.navigate(
                                                                                                    "online_podcast/${item.id}",
                                                                                                )
                                                                                            }

                                                                                            is EpisodeItem -> {
                                                                                                if (!isListenTogetherGuest) {
                                                                                                    playerConnection.playQueue(
                                                                                                        ListQueue(
                                                                                                            title = item.title,
                                                                                                            items =
                                                                                                                listOf(
                                                                                                                    item
                                                                                                                        .toMediaMetadata()
                                                                                                                        .toMediaItem(),
                                                                                                                ),
                                                                                                        ),
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                    onLongClick = {
                                                                                        haptic.performHapticFeedback(
                                                                                            HapticFeedbackType.LongPress,
                                                                                        )
                                                                                        menuState.show {
                                                                                            when (item) {
                                                                                                is SongItem -> {
                                                                                                    YouTubeSongMenu(
                                                                                                        song = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is AlbumItem -> {
                                                                                                    YouTubeAlbumMenu(
                                                                                                        albumItem = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is ArtistItem -> {
                                                                                                    YouTubeArtistMenu(
                                                                                                        artist = item,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is PlaylistItem -> {
                                                                                                    YouTubePlaylistMenu(
                                                                                                        playlist = item,
                                                                                                        coroutineScope = scope,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is PodcastItem -> {
                                                                                                    YouTubePlaylistMenu(
                                                                                                        playlist = item.asPlaylistItem(),
                                                                                                        coroutineScope = scope,
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }

                                                                                                is EpisodeItem -> {
                                                                                                    YouTubeSongMenu(
                                                                                                        song = item.asSongItem(),
                                                                                                        onDismiss = menuState::dismiss,
                                                                                                    )
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                ),
                                                                    )
                                                                }
                                                            } else {
                                                                Spacer(modifier = Modifier.width(itemWidth))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (pagerState.pageCount > 1) {
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .height(24.dp)
                                                        .fillMaxWidth(),
                                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                repeat(pagerState.pageCount) { iteration ->
                                                    val color =
                                                        if (pagerState.currentPage == iteration) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        }
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .padding(4.dp)
                                                                .clip(CircleShape)
                                                                .background(color)
                                                                .size(8.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.QuickPicks -> {
                            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                                item(key = "quick_picks_title") {
                                    val quickPicksTitle = stringResource(R.string.quick_picks)
                                    NavigationTitle(
                                        title = quickPicksTitle,
                                        onPlayAllClick =
                                            if (!isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = quickPicksTitle,
                                                            items = quickPicks.distinctBy { it.id }.map { it.toMediaItem() },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                item(key = "quick_picks_list") {
                                    LazyHorizontalGrid(
                                        state = quickPicksLazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * 4),
                                        ) {
                                            items(
                                                items = quickPicks.distinctBy { it.id },
                                                key = { "home_quickpick_${it.id}" },
                                            ) { originalSong ->
                                            // fetch song from database to keep updated
                                            val song by database
                                                .song(originalSong.id)
                                                .collectAsStateWithLifecycle(initialValue = originalSong)

                                            SongListItem(
                                                song = song!!,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!isListenTogetherGuest) {
                                                                    if (song!!.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            if (autoRadioQueue) {
                                                                                YouTubeQueue.radio(
                                                                                    song!!.toMediaMetadata(),
                                                                                )
                                                                            } else {
                                                                                ListQueue(
                                                                                    title = song!!.title,
                                                                                    items = listOf(song!!.toMediaItem())
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    SongMenu(
                                                                        originalSong = song!!,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            },
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "community_playlists_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.from_the_community),
                                    )
                                }

                                item(key = "community_playlists_content") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(playlists) { item ->
                                            CommunityPlaylistCard(
                                                item = item,
                                                onClick = {
                                                    navController.navigate("online_playlist/${item.playlist.id.removePrefix("VL")}")
                                                },
                                                onSongClick = { song ->
                                                    if (!isListenTogetherGuest) {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.DailyDiscover -> {
                            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
                                item(key = "daily_discover_title") {
                                    val title = stringResource(R.string.your_daily_discover)
                                    NavigationTitle(
                                        title = title,
                                        onPlayAllClick = {
                                            val queueItems =
                                                discoverList.mapNotNull {
                                                    (it.recommendation as? SongItem)?.toMediaMetadata()
                                                }

                                            if (queueItems.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = title,
                                                        items = queueItems.map { it.toMediaItem() },
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }

                                item(key = "daily_discover_content") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(340.dp)
                                                .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val carouselState = rememberCarouselState { discoverList.size }
                                        HorizontalMultiBrowseCarousel(
                                            state = carouselState,
                                            preferredItemWidth = 320.dp,
                                            itemSpacing = 16.dp,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(320.dp),
                                        ) { i ->
                                            val item = discoverList[i]
                                            DailyDiscoverCard(
                                                dailyDiscover = item,
                                                onClick = {
                                                    if (!isListenTogetherGuest) {
                                                        val song = item.recommendation as? SongItem
                                                        val mediaMetadata = song?.toMediaMetadata()
                                                        if (mediaMetadata != null) {
                                                            playerConnection.playQueue(
                                                                if (autoRadioQueue) {
                                                                    YouTubeQueue(
                                                                        song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                        mediaMetadata,
                                                                    )
                                                                } else {
                                                                    ListQueue(
                                                                        title = song.title,
                                                                        items = listOf(song.toMediaItem())
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.KeepListening -> {
                            keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                                item(key = "keep_listening_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.keep_listening),
                                    )
                                }

                                item(key = "keep_listening_list") {
                                    val rows = if (keepListening.size > 6) 2 else 1
                                    LazyHorizontalGrid(
                                        state = remember("keep_listening_grid") { LazyGridState() },
                                        rows = GridCells.Fixed(rows),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(
                                                    (
                                                        currentGridHeight +
                                                            with(LocalDensity.current) {
                                                                MaterialTheme.typography.bodyLarge.lineHeight
                                                                    .toDp() * 2 +
                                                                    MaterialTheme.typography.bodyMedium.lineHeight
                                                                        .toDp() * 2
                                                            }
                                                    ) * rows,
                                                ),
                                    ) {
                                        items(keepListening.distinctBy { it.id }, key = { "home_keep_listening_${it.id}" }) {
                                            localGridItem(it)
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.AccountPlaylists -> {
                            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
                                item(key = "account_playlists_title") {
                                    NavigationTitle(
                                        label = stringResource(R.string.mixes),
                                        title = accountName,
                                        thumbnail = {
                                            if (url != null) {
                                                AsyncImage(
                                                    model =
                                                        ImageRequest
                                                            .Builder(LocalContext.current)
                                                            .data(url)
                                                            .diskCachePolicy(CachePolicy.ENABLED)
                                                            .diskCacheKey(url)
                                                            .crossfade(false)
                                                            .build(),
                                                    placeholder = painterResource(id = R.drawable.person),
                                                    error = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier =
                                                        Modifier
                                                            .size(ListThumbnailSize)
                                                            .clip(CircleShape),
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.person),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(ListThumbnailSize),
                                                )
                                            }
                                        },
                                        onClick = {
                                            navController.navigate("account")
                                        },
                                    )
                                }

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                    ) {
                                        items(
                                            items = accountPlaylists.distinctBy { it.id },
                                            key = { "home_account_playlist_${it.id}" },
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.ForgottenFavorites -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                item(key = "forgotten_favorites_title") {
                                    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
                                    NavigationTitle(
                                        title = forgottenFavoritesTitle,
                                        onPlayAllClick =
                                            if (!isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = forgottenFavoritesTitle,
                                                            items = forgottenFavorites.distinctBy { it.id }.map { it.toMediaItem() },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                item(key = "forgotten_favorites_list") {
                                    // take min in case list size is less than 4
                                    val rows = min(4, forgottenFavorites.size)
                                    LazyHorizontalGrid(
                                        state = forgottenFavoritesLazyGridState,
                                        rows = GridCells.Fixed(rows),
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                        flingBehavior =
                                            rememberSnapFlingBehavior(
                                                forgottenFavoritesSnapLayoutInfoProvider,
                                            ),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * rows),
                                        ) {
                                            items(
                                                items = forgottenFavorites.distinctBy { it.id },
                                                key = { "home_forgotten_${it.id}" },
                                            ) { originalSong ->
                                            val song by database
                                                .song(originalSong.id)
                                                .collectAsStateWithLifecycle(initialValue = originalSong)

                                            SongListItem(
                                                song = song!!,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier =
                                                    Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!isListenTogetherGuest) {
                                                                    if (song!!.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            if (autoRadioQueue) {
                                                                                YouTubeQueue.radio(
                                                                                    song!!.toMediaMetadata(),
                                                                                )
                                                                            } else {
                                                                                ListQueue(
                                                                                    title = song!!.title,
                                                                                    items = listOf(song!!.toMediaItem())
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    SongMenu(
                                                                        originalSong = song!!,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            },
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is HomeSection.SimilarRecommendation -> {
                            val recommendation = similarRecommendations?.getOrNull(section.index)
                            recommendation?.let {
                                item(key = "similar_to_title_${section.index}") {
                                    NavigationTitle(
                                        label = stringResource(R.string.similar_to),
                                        title = recommendation.title.title,
                                        thumbnail =
                                            recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                                                {
                                                    val shape =
                                                        if (recommendation.title is Artist) {
                                                            CircleShape
                                                        } else {
                                                            RoundedCornerShape(
                                                                ThumbnailCornerRadius,
                                                            )
                                                        }
                                                    AsyncImage(
                                                        model = thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier =
                                                            Modifier
                                                                .size(ListThumbnailSize)
                                                                .clip(shape),
                                                    )
                                                }
                                            },
                                        onClick = {
                                            when (recommendation.title) {
                                                is Song -> {
                                                    navController.navigate("album/${recommendation.title.album!!.id}")
                                                }

                                                is Album -> {
                                                    navController.navigate("album/${recommendation.title.id}")
                                                }

                                                is Artist -> {
                                                    navController.navigate("artist/${recommendation.title.id}")
                                                }

                                                is Playlist -> {}
                                            }
                                        },
                                    )
                                }

                                item(key = "similar_to_list_${section.index}") {
                                    LazyRow(
                                        contentPadding =
                                            WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                    ) {
                                        items(recommendation.items.distinctBy { it.id }, key = { "home_similar_${it.id}" }) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }

                        is HomeSection.HomePageSection -> {
                            // Skip HomePageSection rendering when podcast chip is selected
                            // Podcast sections are handled separately with special UI
                            if (selectedChip?.title?.contains("Podcast", ignoreCase = true) == true) {
                                return@forEach
                            }
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                // Check if section contains songs for Play All functionality
                                val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                val hasPlayableSongs = sectionSongs.isNotEmpty()
                                // Check if this section contains ONLY songs (like Quick picks, Trending songs)
                                val isSongsOnlySection =
                                    sectionData.items.isNotEmpty() &&
                                        sectionData.items.all { it is SongItem }

                                item(key = "home_section_title_${section.index}") {
                                    NavigationTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        thumbnail =
                                            sectionData.thumbnail?.let { thumbnailUrl ->
                                                {
                                                    val shape =
                                                        if (sectionData.endpoint?.isArtistEndpoint == true) {
                                                            CircleShape
                                                        } else {
                                                            RoundedCornerShape(
                                                                ThumbnailCornerRadius,
                                                            )
                                                        }
                                                    AsyncImage(
                                                        model = thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier =
                                                            Modifier
                                                                .size(ListThumbnailSize)
                                                                .clip(shape),
                                                    )
                                                }
                                            },
                                        onClick =
                                            sectionData.endpoint?.let { endpoint ->
                                                {
                                                    when {
                                                        endpoint.browseId == "FEmusic_moods_and_genres" -> {
                                                            navController.navigate("mood_and_genres")
                                                        }

                                                        // Handle podcast-related browse endpoints
                                                        endpoint.browseId.startsWith("FEmusic_library_non_music_audio") ||
                                                            endpoint.browseId.startsWith("FEmusic_non_music_audio") -> {
                                                            navController.navigate("youtube_browse/${endpoint.browseId}")
                                                        }

                                                        endpoint.params != null -> {
                                                            navController.navigate(
                                                                "youtube_browse/${endpoint.browseId}?params=${endpoint.params}",
                                                            )
                                                        }

                                                        else -> {
                                                            navController.navigate("browse/${endpoint.browseId}")
                                                        }
                                                    }
                                                }
                                            },
                                        onPlayAllClick =
                                            if (hasPlayableSongs && !isListenTogetherGuest) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = sectionData.title,
                                                            items = sectionSongs.map { it.toMediaMetadata().toMediaItem() },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }

                                if (isSongsOnlySection) {
                                    // Render songs as a horizontal scrollable list (like Quick picks in YouTube Music)
                                    item(key = "home_section_list_${section.index}") {
                                        LazyHorizontalGrid(
                                            state = remember("section_${section.index}_grid") { LazyGridState() },
                                            rows = GridCells.Fixed(4),
                                            contentPadding =
                                                WindowInsets.systemBars
                                                    .only(WindowInsetsSides.Horizontal)
                                                    .asPaddingValues(),
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(ListItemHeight * 4),
                                        ) {
                                            items(
                                                items = sectionSongs.distinctBy { it.id },
                                                key = { "home_section_${section.index}_song_${it.id}" },
                                            ) { song ->
                                                YouTubeListItem(
                                                    item = song,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    trailingContent = {
                                                        IconButton(
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                                }
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.more_vert),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .width(horizontalLazyGridItemWidth)
                                                            .combinedClickable(
                                                                onClick = {
                                                                    if (!isListenTogetherGuest) {
                                                                        playerConnection.playQueue(
                                                                            if (autoRadioQueue) {
                                                                                YouTubeQueue(
                                                                                    song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                                    song.toMediaMetadata(),
                                                                                )
                                                                            } else {
                                                                                ListQueue(
                                                                                    title = song.title,
                                                                                    items = listOf(song.toMediaItem())
                                                                                )
                                                                            }
                                                                        )
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    menuState.show {
                                                                        YouTubeSongMenu(
                                                                            song = song,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }
                                                                },
                                                            ),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Render mixed content as horizontal grid items (albums, playlists, artists, etc.)
                                    item(key = "home_section_list_${section.index}") {
                                        LazyRow(
                                            contentPadding =
                                                WindowInsets.systemBars
                                                    .only(WindowInsetsSides.Horizontal)
                                                    .asPaddingValues(),
                                        ) {
                                            items(
                                                items = sectionData.items.distinctBy { it.id },
                                                key = { "home_section_${section.index}_item_${it.id}" },
                                            ) { item ->
                                                ytGridItem(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.MoodAndGenres -> {
                            // Skip MoodAndGenres when podcast chip is selected
                            if (selectedChip?.title?.contains("Podcast", ignoreCase = true) == true) {
                                return@forEach
                            }
                            explorePage?.moodAndGenres?.let { moodAndGenres ->
                                item(key = "mood_and_genres_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.mood_and_genres),
                                        onClick = {
                                            navController.navigate("mood_and_genres")
                                        },
                                    )
                                }
                                item(key = "mood_and_genres_list") {
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(4),
                                        contentPadding = PaddingValues(6.dp),
                                        modifier =
                                            Modifier
                                                .height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
                                    ) {
                                        items(moodAndGenres.distinctBy { "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}" }, key = { "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}" }) {
                                            MoodAndGenresButton(
                                                title = it.title,
                                                onClick = {
                                                    navController.navigate(
                                                        "youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}",
                                                    )
                                                },
                                                modifier =
                                                    Modifier
                                                        .padding(6.dp)
                                                        .width(180.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Only show shimmer during initial loading, not for pagination
                if (isLoading && homePage?.sections.isNullOrEmpty()) {
                    item(key = "loading_shimmer") {
                        ShimmerHost(
                        ) {
                            repeat(2) {
                                TextPlaceholder(
                                    height = 36.dp,
                                    modifier =
                                        Modifier
                                            .padding(12.dp)
                                            .width(250.dp),
                                )
                                LazyRow(
                                    contentPadding =
                                        WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                ) {
                                    items(4) {
                                        GridItemPlaceHolder()
                                    }
                                }
                            }

                            TextPlaceholder(
                                height = 36.dp,
                                modifier =
                                    Modifier
                                        .padding(vertical = 12.dp, horizontal = 12.dp)
                                        .width(250.dp),
                            )
                            repeat(4) {
                                Row {
                                    repeat(2) {
                                        TextPlaceholder(
                                            height = MoodAndGenresButtonHeight,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier =
                                                Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .width(200.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HideOnScrollFAB(
                visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
                lazyListState = lazylistState,
                icon = R.drawable.shuffle,
                onClick = {
                    if (!isListenTogetherGuest) {
                        val local =
                            when {
                                allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                                allLocalItems.isNotEmpty() -> true
                                else -> false
                            }
                        scope.launch(Dispatchers.Main) {
                            if (local) {
                                when (val luckyItem = allLocalItems.random()) {
                                    is Song -> {
                                        playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                    }

                                    is Album -> {
                                        val albumWithSongs =
                                            withContext(Dispatchers.IO) {
                                                database.albumWithSongs(luckyItem.id).first()
                                            }
                                        albumWithSongs?.let {
                                            playerConnection.playQueue(LocalAlbumRadio(it))
                                        }
                                    }

                                    is Artist -> {}

                                    is Playlist -> {}
                                }
                            } else {
                                when (val luckyItem = allYtItems.random()) {
                                    is SongItem -> {
                                        playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                    }

                                    is AlbumItem -> {
                                        playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                                    }

                                    is ArtistItem -> {
                                        luckyItem.radioEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    }

                                    is PlaylistItem -> {
                                        luckyItem.playEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    }

                                    is PodcastItem -> {
                                        luckyItem.playEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    }

                                    is EpisodeItem -> {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = luckyItem.title,
                                                items = listOf(luckyItem.toMediaMetadata().toMediaItem()),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onRecognitionClick = {
                    navController.navigate("recognition")
                },
            )
        }
    }
}
